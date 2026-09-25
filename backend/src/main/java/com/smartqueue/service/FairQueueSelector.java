package com.smartqueue.service;

import com.smartqueue.model.PriorityType;
import com.smartqueue.model.QueueEntry;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class FairQueueSelector {

    private static final int MAX_PRIORITY_JUMP_POSITIONS = 2;
    private static final long REGULAR_STARVATION_LIMIT_MINUTES = 10;

    public QueueEntry selectNext(List<QueueEntry> waitingEntries) {

        if (waitingEntries.isEmpty()) {
            throw new IllegalStateException("No waiting customers found");
        }

        QueueEntry oldestCustomer = waitingEntries.get(0);

        boolean oldestRegularHasWaitedTooLong =
                oldestCustomer.getPriorityType() == PriorityType.REGULAR
                        && Duration.between(
                        oldestCustomer.getAssignedAt(),
                        LocalDateTime.now()
                ).toMinutes() >= REGULAR_STARVATION_LIMIT_MINUTES;

        if (oldestRegularHasWaitedTooLong) {
            return oldestCustomer;
        }

        for (int index = 0; index < waitingEntries.size(); index++) {
            QueueEntry candidate = waitingEntries.get(index);

            if (candidate.getPriorityType() == PriorityType.ASSISTANCE
                    && index <= MAX_PRIORITY_JUMP_POSITIONS) {
                return candidate;
            }
        }

        return oldestCustomer;
    }
}