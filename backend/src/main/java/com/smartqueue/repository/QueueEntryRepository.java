package com.smartqueue.repository;

import com.smartqueue.model.QueueEntry;
import com.smartqueue.model.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QueueEntryRepository
        extends JpaRepository<QueueEntry, Long> {

    List<QueueEntry> findByStatusInOrderByAssignedAtAsc(
            Collection<QueueStatus> statuses);

    List<QueueEntry> findByCounterIdAndStatusInOrderByAssignedAtAsc(
            Long counterId,
            Collection<QueueStatus> statuses);

    List<QueueEntry> findByCounterIdAndStatusOrderByAssignedAtAsc(
            Long counterId,
            QueueStatus status);

    Optional<QueueEntry> findFirstByCounterIdAndStatus(
            Long counterId,
            QueueStatus status);

    long countByStatus(QueueStatus status);
}
