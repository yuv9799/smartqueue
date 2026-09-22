package com.smartqueue.service;

import com.smartqueue.dto.JoinQueueRequest;
import com.smartqueue.dto.QueueAssignmentResponse;
import com.smartqueue.dto.QueueEntryResponse;
import com.smartqueue.model.QueueEntry;
import com.smartqueue.model.QueueStatus;
import com.smartqueue.repository.QueueEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QueueService {

    private final QueueEntryRepository queueEntryRepository;
    private final CheckoutTimePredictor checkoutTimePredictor;
    private final CounterAllocator counterAllocator;

    public QueueService(
            QueueEntryRepository queueEntryRepository,
            CheckoutTimePredictor checkoutTimePredictor,
            CounterAllocator counterAllocator
    ) {
        this.queueEntryRepository = queueEntryRepository;
        this.checkoutTimePredictor = checkoutTimePredictor;
        this.counterAllocator = counterAllocator;
    }

    @Transactional
    public QueueAssignmentResponse joinQueue(JoinQueueRequest request) {

        var prediction = checkoutTimePredictor.predict(
                request.itemCount(),
                request.paymentMethod()
        );

        var allocation = counterAllocator.allocateCounter();

        QueueEntry entry = new QueueEntry(
                request.customerName().trim(),
                request.itemCount(),
                request.paymentMethod(),
                request.priorityType(),
                prediction.seconds(),
                prediction.source(),
                allocation.counter()
        );

        QueueEntry savedEntry = queueEntryRepository.saveAndFlush(entry);

        String token = "SQ-%04d".formatted(savedEntry.getId());
        savedEntry.setToken(token);

        savedEntry = queueEntryRepository.save(savedEntry);

        return QueueAssignmentResponse.from(
                savedEntry,
                allocation.estimatedWaitSeconds()
        );
    }

    @Transactional(readOnly = true)
    public List<QueueEntryResponse> getActiveQueue() {

        return queueEntryRepository
                .findByStatusInOrderByAssignedAtAsc(
                        List.of(
                                QueueStatus.WAITING,
                                QueueStatus.SERVING
                        )
                )
                .stream()
                .map(QueueEntryResponse::from)
                .toList();
    }
    @Transactional
    public QueueEntryResponse startNextCustomer(Long counterId) {

        boolean customerAlreadyServing =
                queueEntryRepository
                        .findFirstByCounterIdAndStatus(
                                counterId,
                                QueueStatus.SERVING
                        )
                        .isPresent();

        if (customerAlreadyServing) {
            throw new IllegalStateException(
                    "This counter is already serving a customer"
            );
        }

        QueueEntry nextCustomer =
                queueEntryRepository
                        .findByCounterIdAndStatusOrderByAssignedAtAsc(
                                counterId,
                                QueueStatus.WAITING
                        )
                        .stream()
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "No waiting customer found for this counter"
                                )
                        );

        nextCustomer.startService();

        QueueEntry savedEntry =
                queueEntryRepository.save(nextCustomer);

        return QueueEntryResponse.from(savedEntry);
    }

    @Transactional
    public QueueEntryResponse completeCustomer(Long entryId) {

        QueueEntry entry = queueEntryRepository
                .findById(entryId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Queue entry not found with ID: " + entryId
                        )
                );

        entry.completeService();

        QueueEntry savedEntry =
                queueEntryRepository.save(entry);

        return QueueEntryResponse.from(savedEntry);
    }
}