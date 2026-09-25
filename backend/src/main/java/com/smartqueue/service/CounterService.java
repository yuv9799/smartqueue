package com.smartqueue.service;
import com.smartqueue.dto.CounterStatusUpdateRequest;
import com.smartqueue.model.CheckoutCounter;
import com.smartqueue.model.CounterStatus;
import com.smartqueue.model.QueueStatus;
import com.smartqueue.repository.CheckoutCounterRepository;
import com.smartqueue.repository.QueueEntryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CounterService {
    private final CheckoutCounterRepository checkoutCounterRepository;
    private final QueueEntryRepository queueEntryRepository;

    public CounterService(
            CheckoutCounterRepository checkoutCounterRepository,
            QueueEntryRepository queueEntryRepository
    ) {
        this.checkoutCounterRepository = checkoutCounterRepository;
        this.queueEntryRepository = queueEntryRepository;
    }

    @Transactional(readOnly = true)
    public List<CheckoutCounter> getAllCounters() {
        return checkoutCounterRepository.findAll(
                Sort.by(Sort.Direction.ASC, "id")
        );
    }

    @Transactional
    public CheckoutCounter updateCounterStatus(
            Long counterId,
            CounterStatusUpdateRequest request
    ) {
        CheckoutCounter counter =
                checkoutCounterRepository
                        .findById(counterId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Counter not found with ID: "
                                                + counterId
                                )
                        );

        CounterStatus requestedStatus = request.status();

        if (counter.getStatus() == requestedStatus) {
            return counter;
        }

        if (requestedStatus == CounterStatus.CLOSED) {

            boolean hasActiveCustomers =
                    !queueEntryRepository
                            .findByCounterIdAndStatusInOrderByAssignedAtAsc(
                                    counterId,
                                    List.of(
                                            QueueStatus.WAITING,
                                            QueueStatus.SERVING
                                    )
                            )
                            .isEmpty();

            if (hasActiveCustomers) {
                throw new IllegalStateException(
                        "Cannot close a counter with active customers"
                );
            }
        }

        counter.setStatus(requestedStatus);

        return checkoutCounterRepository.save(counter);
    }
}
