package com.smartqueue.service;

import com.smartqueue.model.CheckoutCounter;
import com.smartqueue.model.CounterStatus;
import com.smartqueue.model.QueueEntry;
import com.smartqueue.model.QueueStatus;
import com.smartqueue.repository.CheckoutCounterRepository;
import com.smartqueue.repository.QueueEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

@Service
public class CounterAllocator {
    private final CheckoutCounterRepository counterRepository;
    private final QueueEntryRepository queueEntryRepository;

    public CounterAllocator(
            CheckoutCounterRepository counterRepository,
            QueueEntryRepository queueEntryRepository) {

        this.counterRepository = counterRepository;
        this.queueEntryRepository = queueEntryRepository;
    }

    @Transactional(readOnly = true)
    public Allocation allocateCounter() {

        List<CheckoutCounter> openCounters =
                counterRepository.findByStatus(CounterStatus.OPEN);

        if (openCounters.isEmpty()) {
            throw new IllegalStateException(
                    "No checkout counter is currently open");
        }

        List<QueueStatus> activeStatuses = List.of(
                QueueStatus.WAITING,
                QueueStatus.SERVING
        );

        List<CounterLoad> counterLoads = new ArrayList<>();

        for (CheckoutCounter counter : openCounters) {

            List<QueueEntry> activeEntries =
                    queueEntryRepository
                            .findByCounterIdAndStatusInOrderByAssignedAtAsc(
                                    counter.getId(),
                                    activeStatuses
                            );

            long workloadSeconds = activeEntries.stream()
                    .mapToLong(this::calculateRemainingWork)
                    .sum();

            counterLoads.add(
                    new CounterLoad(counter, workloadSeconds)
            );
        }

        PriorityQueue<CounterLoad> minHeap =
                new PriorityQueue<>(counterLoads);

        CounterLoad bestCounter = minHeap.peek();

        return new Allocation(
                bestCounter.counter(),
                bestCounter.workloadSeconds(),
                openCounters.size()
        );
    }

    private long calculateRemainingWork(QueueEntry entry) {

        if (entry.getStatus() == QueueStatus.SERVING &&
                entry.getServiceStartedAt() != null) {

            long elapsedSeconds = Duration.between(
                    entry.getServiceStartedAt(),
                    LocalDateTime.now()
            ).toSeconds();

            return Math.max(
                    0,
                    entry.getPredictedServiceSeconds() - elapsedSeconds
            );
        }

        return entry.getPredictedServiceSeconds();
    }

    private record CounterLoad(
            CheckoutCounter counter,
            long workloadSeconds)
            implements Comparable<CounterLoad> {

        @Override
        public int compareTo(CounterLoad other) {

            int workloadComparison = Long.compare(
                    this.workloadSeconds,
                    other.workloadSeconds
            );

            if (workloadComparison != 0) {
                return workloadComparison;
            }

            return Long.compare(
                    this.counter.getId(),
                    other.counter.getId()
            );
        }
    }

    public record Allocation(
            CheckoutCounter counter,
            long estimatedWaitSeconds,
            int openCounterCount) {
    }
}
