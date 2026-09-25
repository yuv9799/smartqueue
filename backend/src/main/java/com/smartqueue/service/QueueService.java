package com.smartqueue.service;

import com.smartqueue.dto.JoinQueueRequest;
import com.smartqueue.dto.QueueAssignmentResponse;
import com.smartqueue.dto.QueueEntryResponse;
import com.smartqueue.model.QueueEntry;
import com.smartqueue.model.QueueStatus;
import com.smartqueue.repository.QueueEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class QueueService {

    private final QueueEntryRepository queueEntryRepository;
    private final CheckoutTimePredictor checkoutTimePredictor;
    private final MlPredictionClient mlPredictionClient;
    private final CounterAllocator counterAllocator;
    private final FairQueueSelector fairQueueSelector;

    public QueueService(
            QueueEntryRepository queueEntryRepository,
            CheckoutTimePredictor checkoutTimePredictor,
            MlPredictionClient mlPredictionClient,
            CounterAllocator counterAllocator,
            FairQueueSelector fairQueueSelector
    ) {
        this.queueEntryRepository = queueEntryRepository;
        this.checkoutTimePredictor = checkoutTimePredictor;
        this.mlPredictionClient = mlPredictionClient;
        this.counterAllocator = counterAllocator;
        this.fairQueueSelector = fairQueueSelector;
    }

    @Transactional
    public QueueAssignmentResponse joinQueue(JoinQueueRequest request) {

        var allocation = counterAllocator.allocateCounter();

        // Snapshot of live queue state used as ML features
        long queueLength =
                queueEntryRepository.countByStatus(QueueStatus.WAITING)
                        + queueEntryRepository.countByStatus(QueueStatus.SERVING);
        int queuePosition = queueLength == 0 ? 1 : (int) queueLength + 1;
        double counterLoadMinutes = allocation.estimatedWaitSeconds() / 60.0;

        // ML-first prediction, rule-based fallback when the sidecar is down
        int predictedSeconds;
        String predictionSource;
        Long estimatedWaitSeconds = allocation.estimatedWaitSeconds();
        Double abandonmentRisk = null;
        boolean assistanceRequired =
                request.priorityType() == com.smartqueue.model.PriorityType.ASSISTANCE;

        var mlFeatures = MlPredictionClient.MlFeatures.fromJoinParams(
                request.itemCount(),
                request.paymentMethod().name(),
                assistanceRequired ? 1 : 0,
                allocation.openCounterCount(),
                (int) queueLength,
                queuePosition,
                counterLoadMinutes
        );

        var ml = mlPredictionClient.predict(mlFeatures);
        if (ml != null) {
            predictedSeconds = ml.serviceSeconds();
            predictionSource = ml.source();
            estimatedWaitSeconds = ml.waitSeconds();
            abandonmentRisk = ml.abandonmentProbability();
        } else {
            var prediction = checkoutTimePredictor.predict(
                    request.itemCount(),
                    request.paymentMethod()
            );
            predictedSeconds = prediction.seconds();
            predictionSource = prediction.source();
        }

        QueueEntry entry = new QueueEntry(
                request.customerName().trim(),
                request.itemCount(),
                request.paymentMethod(),
                request.priorityType(),
                predictedSeconds,
                predictionSource,
                allocation.counter()
        );

        QueueEntry savedEntry = queueEntryRepository.saveAndFlush(entry);

        String token = "SQ-%04d".formatted(savedEntry.getId());
        savedEntry.setToken(token);

        savedEntry = queueEntryRepository.save(savedEntry);

        return QueueAssignmentResponse.from(
                savedEntry,
                estimatedWaitSeconds,
                abandonmentRisk
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

    /**
     * Returns paginated, filterable history of every queue entry ever recorded.
     *
     * @param status    optional status filter
     * @param counterId optional counter filter
     * @param from      optional start of arrival-time range (inclusive)
     * @param to        optional end of arrival-time range (inclusive)
     * @param page      zero-based page number
     * @param size      page size
     */
    @Transactional(readOnly = true)
    public Page<QueueEntryResponse> getHistory(
            QueueStatus status,
            Long counterId,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 500);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<QueueEntry> result;

        if (counterId != null && status != null && from != null && to != null) {
            result = queueEntryRepository
                    .findByCounterIdAndStatusAndArrivalTimeBetweenOrderByArrivalTimeDesc(
                            counterId, status, from, to, pageable);
        } else if (counterId != null && status != null) {
            result = queueEntryRepository
                    .findByCounterIdAndStatusOrderByArrivalTimeDesc(
                            counterId, status, pageable);
        } else if (counterId != null && from != null && to != null) {
            result = queueEntryRepository
                    .findByCounterIdAndArrivalTimeBetweenOrderByArrivalTimeDesc(
                            counterId, from, to, pageable);
        } else if (counterId != null) {
            result = queueEntryRepository
                    .findByCounterIdOrderByArrivalTimeDesc(counterId, pageable);
        } else if (status != null && from != null && to != null) {
            result = queueEntryRepository
                    .findByStatusAndArrivalTimeBetweenOrderByArrivalTimeDesc(
                            status, from, to, pageable);
        } else if (status != null) {
            result = queueEntryRepository
                    .findByStatusOrderByArrivalTimeDesc(status, pageable);
        } else if (from != null && to != null) {
            result = queueEntryRepository
                    .findByArrivalTimeBetweenOrderByArrivalTimeDesc(
                            from, to, pageable);
        } else {
            result = queueEntryRepository.findAll(pageable);
        }

        return result.map(QueueEntryResponse::from);
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

        List<QueueEntry> waitingCustomers =
                queueEntryRepository
                        .findByCounterIdAndStatusOrderByAssignedAtAsc(
                                counterId,
                                QueueStatus.WAITING
                        );

        QueueEntry nextCustomer =
                fairQueueSelector.selectNext(waitingCustomers);

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

    @Transactional
    public QueueEntryResponse cancelCustomer(Long entryId) {

        QueueEntry entry = queueEntryRepository
                .findById(entryId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Queue entry not found with ID: " + entryId
                        )
                );

        entry.cancel();

        QueueEntry savedEntry =
                queueEntryRepository.save(entry);

        return QueueEntryResponse.from(savedEntry);
    }
}
