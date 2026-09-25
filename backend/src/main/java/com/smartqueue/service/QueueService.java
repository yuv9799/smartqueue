package com.smartqueue.service;

import com.smartqueue.dto.JoinQueueRequest;
import com.smartqueue.dto.QueueAssignmentResponse;
import com.smartqueue.dto.QueueEntryResponse;
import com.smartqueue.model.CheckoutCounter;
import com.smartqueue.model.CounterStatus;
import com.smartqueue.model.QueueEntry;
import com.smartqueue.model.QueueStatus;
import com.smartqueue.repository.CheckoutCounterRepository;
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
    private final CheckoutCounterRepository checkoutCounterRepository;
    private final CheckoutTimePredictor checkoutTimePredictor;
    private final MlPredictionClient mlPredictionClient;
    private final CounterAllocator counterAllocator;
    private final FairQueueSelector fairQueueSelector;

    public QueueService(
            QueueEntryRepository queueEntryRepository,
            CheckoutCounterRepository checkoutCounterRepository,
            CheckoutTimePredictor checkoutTimePredictor,
            MlPredictionClient mlPredictionClient,
            CounterAllocator counterAllocator,
            FairQueueSelector fairQueueSelector
    ) {
        this.queueEntryRepository = queueEntryRepository;
        this.checkoutCounterRepository = checkoutCounterRepository;
        this.checkoutTimePredictor = checkoutTimePredictor;
        this.mlPredictionClient = mlPredictionClient;
        this.counterAllocator = counterAllocator;
        this.fairQueueSelector = fairQueueSelector;
    }

    @Transactional
    public QueueAssignmentResponse joinQueue(JoinQueueRequest request) {

        // --- 1. Snapshot queue state (read-only, no side effects) ---
        long queueLength =
                queueEntryRepository.countByStatus(QueueStatus.WAITING)
                        + queueEntryRepository.countByStatus(QueueStatus.SERVING);
        int queuePosition = queueLength == 0 ? 1 : (int) queueLength + 1;

        // --- 2. Lightweight rule-based estimate — no network, needed for
        //        counter-steering before the ML call ---
        var rulePrediction = checkoutTimePredictor.predict(
                request.itemCount(),
                request.paymentMethod()
        );
        int initialServiceSeconds = rulePrediction.seconds();

        // --- 3. Pre-select the least-loaded counter (existing load only) to
        //        feed a REAL counter-load into the ML feature vector. The final,
        //        prediction-aware selection happens in step 5. ---
        var preAllocation = counterAllocator.allocateCounter(0);
        double counterLoadMinutes = preAllocation.estimatedWaitSeconds() / 60.0;

        // --- 4. ML prediction (wait-time + abandonment; service-time from ML
        //        is secondary; the rule estimate steers the counter choice) ---
        int predictedSeconds = initialServiceSeconds;
        String predictionSource = rulePrediction.source();
        Long estimatedWaitSeconds = null;
        Double abandonmentRisk = null;

        boolean assistanceRequired =
                request.priorityType() == com.smartqueue.model.PriorityType.ASSISTANCE;

        var mlFeatures = MlPredictionClient.MlFeatures.fromJoinParams(
                request.itemCount(),
                request.paymentMethod().name(),
                assistanceRequired ? 1 : 0,
                preAllocation.openCounterCount(),
                (int) queueLength,
                queuePosition,
                counterLoadMinutes
        );

        var ml = mlPredictionClient.predict(mlFeatures);
        if (ml != null) {
            // ML's wait/abandonment replace the rule estimate;
            // service time may be upgraded to ML's if available.
            predictionSource = ml.source();
            estimatedWaitSeconds = ml.waitSeconds();
            abandonmentRisk = ml.abandonmentProbability();
            // ML service time may differ from the rule estimate; trust the
            // trained model for the stored record when it is available.
            predictedSeconds = ml.serviceSeconds();
        }

        if (estimatedWaitSeconds == null) {
            estimatedWaitSeconds = preAllocation.estimatedWaitSeconds();
        }

        // --- 5. Final counter allocation — now includes the new customer's
        //        own predicted service time so the least-loaded counter wins
        //        for THIS customer, not just for existing customers.
        var allocation = counterAllocator.allocateCounter(initialServiceSeconds);

        // --- 6. Persist ---
        QueueEntry entry = new QueueEntry(
                request.customerName().trim(),
                request.itemCount(),
                request.paymentMethod(),
                request.priorityType(),
                predictedSeconds,
                predictionSource,
                allocation.counter(),
                estimatedWaitSeconds,
                abandonmentRisk
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

    /**
     * Moves a waiting customer to a different checkout counter.
     *
     * @param entryId       ID of the queue entry to reassign (must be WAITING)
     * @param newCounterId  ID of the target counter (must be OPEN)
     */
    @Transactional
    public QueueEntryResponse reassignCustomer(Long entryId, Long newCounterId) {

        QueueEntry entry = queueEntryRepository
                .findById(entryId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Queue entry not found with ID: " + entryId
                        )
                );

        if (entry.getStatus() != QueueStatus.WAITING) {
            throw new IllegalStateException(
                    "Only a waiting customer can be reassigned. "
                            + "Current status: " + entry.getStatus()
            );
        }

        CheckoutCounter newCounter = checkoutCounterRepository
                .findById(newCounterId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Counter not found with ID: " + newCounterId
                        )
                );

        if (newCounter.getStatus() != CounterStatus.OPEN) {
            throw new IllegalStateException(
                    "Cannot reassign to a closed counter: " + newCounter.getName()
            );
        }

        entry.setCounter(newCounter);
        QueueEntry savedEntry = queueEntryRepository.save(entry);

        return QueueEntryResponse.from(savedEntry);
    }
}
