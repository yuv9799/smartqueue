package com.smartqueue;

import com.smartqueue.model.CheckoutCounter;
import com.smartqueue.model.CounterStatus;
import com.smartqueue.model.PaymentMethod;
import com.smartqueue.model.PriorityType;
import com.smartqueue.model.QueueEntry;
import com.smartqueue.model.QueueStatus;
import com.smartqueue.repository.CheckoutCounterRepository;
import com.smartqueue.repository.QueueEntryRepository;
import com.smartqueue.service.MlPredictionClient;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JPA persistence tests for the PostgreSQL-backed SmartQueue domain.
 *
 * <p>These run against the live PostgreSQL database and verify that
 * entities, the counter_id relationship, and repository query behaviour
 * all survive real persistence — not just the entity definitions.
 */
@SpringBootTest
@ActiveProfiles("test")
class QueuePersistenceTests {

    @Autowired
    private QueueEntryRepository queueEntryRepository;

    @Autowired
    private CheckoutCounterRepository counterRepository;

    @Autowired
    private EntityManager entityManager;

    @TestConfiguration
    static class MlDisabledConfig {
        /** Always-return-null client so tests never depend on the ML sidecar. */
        @Bean
        @Primary
        MlPredictionClient mlPredictionClient() {
            MlPredictionClient mock = mock(MlPredictionClient.class);
            when(mock.predict(any())).thenReturn(null);
            return mock;
        }
    }

    @Test
    @Transactional
    void checkoutCounterPersists() {
        CheckoutCounter counter = new CheckoutCounter("Persistence Test Counter", CounterStatus.OPEN);

        CheckoutCounter saved = counterRepository.save(counter);

        assertNotNull(saved.getId());
        assertEquals("Persistence Test Counter", saved.getName());
        assertEquals(CounterStatus.OPEN, saved.getStatus());
    }

    @Test
    @Transactional
    void queueEntryPersistsWithCounterRelationship() {
        CheckoutCounter counter = counterRepository.save(
                new CheckoutCounter("Entry Relationship Counter", CounterStatus.OPEN));

        QueueEntry entry = new QueueEntry(
                "Persistence Test User",
                10,
                PaymentMethod.UPI,
                PriorityType.REGULAR,
                95,
                "RULE_BASELINE_V1",
                counter,
                95L,
                0.05);
        entry.setToken("SQ-PTEST-1");

        QueueEntry saved = queueEntryRepository.saveAndFlush(entry);
        entityManager.clear(); // drop first-level cache — force a real DB read

        QueueEntry fetched = queueEntryRepository.findById(saved.getId())
                .orElseThrow();

        // The FK relationship must survive round-trip to PostgreSQL.
        assertNotNull(fetched.getCounter());
        assertEquals(counter.getId(), fetched.getCounter().getId());
        assertEquals(QueueStatus.WAITING, fetched.getStatus());
        assertEquals("SQ-PTEST-1", fetched.getToken());
    }

    @Test
    @Transactional
    void counterIdNotNullConstraintEnforced() {
        // Insert a row with NULL counter_id via raw SQL; the NOT NULL constraint
        // on the foreign key must cause a DataIntegrityViolationException.
        // If the FK column allowed nulls, this test would pass incorrectly.
        assertThrows(
                jakarta.persistence.PersistenceException.class,
                () -> entityManager.createNativeQuery(
                        "INSERT INTO queue_entries " +
                                "(token,customer_name,item_count,payment_method," +
                                "priority_type,status,predicted_service_seconds," +
                                "prediction_source,counter_id," +
                                "arrival_time,assigned_at) " +
                                "VALUES " +
                                "('SQ-NULLFK','Null FK Test',3,'CASH'," +
                                "'REGULAR','WAITING',45,'RULE_BASELINE_V1',NULL," +
                                "NOW(),NOW())")
                        .executeUpdate()
        );
    }

    @Test
    @Transactional
    void findByStatusInOrderByAssignedAtAscReturnsWaitingAndServing() {
        CheckoutCounter counter = counterRepository
                .findByStatus(CounterStatus.OPEN)
                .stream()
                .findFirst()
                .orElseGet(() -> counterRepository.save(
                        new CheckoutCounter("Query Test Counter", CounterStatus.OPEN)));

        QueueEntry waiting = new QueueEntry(
                "Query Waiting User",
                4,
                PaymentMethod.UPI,
                PriorityType.REGULAR,
                60,
                "RULE_BASELINE_V1",
                counter,
                60L,
                0.02);
        waiting.setToken("SQ-Q1");
        queueEntryRepository.saveAndFlush(waiting);

        List<QueueEntry> active = queueEntryRepository.findByStatusInOrderByAssignedAtAsc(
                List.of(QueueStatus.WAITING, QueueStatus.SERVING));

        assertFalse(active.isEmpty());
        assertTrue(active.stream().allMatch(
                e -> e.getStatus() == QueueStatus.WAITING
                        || e.getStatus() == QueueStatus.SERVING));
    }

    }
