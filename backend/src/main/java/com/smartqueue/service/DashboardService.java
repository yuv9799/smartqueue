package com.smartqueue.service;

import com.smartqueue.dto.DashboardStatsResponse;
import com.smartqueue.model.CounterStatus;
import com.smartqueue.model.QueueStatus;
import com.smartqueue.repository.CheckoutCounterRepository;
import com.smartqueue.repository.QueueEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final QueueEntryRepository queueEntryRepository;
    private final CheckoutCounterRepository checkoutCounterRepository;

    public DashboardService(
            QueueEntryRepository queueEntryRepository,
            CheckoutCounterRepository checkoutCounterRepository
    ) {
        this.queueEntryRepository = queueEntryRepository;
        this.checkoutCounterRepository = checkoutCounterRepository;
    }

    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {

        long waitingCustomers =
                queueEntryRepository.countByStatus(QueueStatus.WAITING);

        long servingCustomers =
                queueEntryRepository.countByStatus(QueueStatus.SERVING);

        long completedCustomers =
                queueEntryRepository.countByStatus(QueueStatus.COMPLETED);

        long activeCustomers = waitingCustomers + servingCustomers;

        long totalCounters =
                checkoutCounterRepository.count();

        long openCounters =
                checkoutCounterRepository.countByStatus(CounterStatus.OPEN);

        double avgPredicted =
                nullToZero(queueEntryRepository
                        .averagePredictedServiceSecondsAllTime());

        double avgActual =
                nullToZero(queueEntryRepository
                        .averageActualServiceSecondsAllTime());

        double mae =
                nullToZero(queueEntryRepository
                        .averagePredictionErrorSecondsAllTime());

        return new DashboardStatsResponse(
                waitingCustomers,
                servingCustomers,
                completedCustomers,
                activeCustomers,
                openCounters,
                totalCounters,
                round(avgPredicted),
                round(avgActual),
                round(mae)
        );
    }

    private static double nullToZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
