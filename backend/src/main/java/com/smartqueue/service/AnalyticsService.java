package com.smartqueue.service;

import com.smartqueue.dto.AnalyticsOverviewResponse;
import com.smartqueue.model.QueueStatus;
import com.smartqueue.repository.QueueEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Report-generation layer for the manager dashboard.
 *
 * <p>All aggregates are computed server-side against the time window
 * (defaults to the last 7 days when no window is supplied).
 */
@Service
public class AnalyticsService {

    private final QueueEntryRepository queueEntryRepository;

    public AnalyticsService(QueueEntryRepository queueEntryRepository) {
        this.queueEntryRepository = queueEntryRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse overview(
            LocalDateTime from,
            LocalDateTime to
    ) {
        LocalDateTime windowFrom = from != null
                ? from
                : LocalDateTime.now().minusDays(7);
        LocalDateTime windowTo = to != null
                ? to
                : LocalDateTime.now().plusDays(1);

        long served = queueEntryRepository
                .countByStatusAndArrivalTimeBetween(
                        QueueStatus.COMPLETED, windowFrom, windowTo);
        long cancelled = queueEntryRepository
                .countByStatusAndArrivalTimeBetween(
                        QueueStatus.CANCELLED, windowFrom, windowTo);

        Double avgWait = queueEntryRepository
                .averageWaitSecondsBetween(windowFrom, windowTo);
        Double avgActual = queueEntryRepository
                .averageActualServiceSecondsBetween(windowFrom, windowTo);
        Double avgPredicted = queueEntryRepository
                .averagePredictedServiceSecondsBetween(windowFrom, windowTo);
        Double mae = queueEntryRepository
                .averagePredictionErrorSecondsBetween(windowFrom, windowTo);

        Double waitMae = queueEntryRepository
                .averageWaitPredictionErrorSecondsBetween(windowFrom, windowTo);

        Double meanAbandonmentRisk = queueEntryRepository
                .averageAbandonmentProbabilityBetween(windowFrom, windowTo);

        List<AnalyticsOverviewResponse.CounterUtilizationResponse> counters =
                queueEntryRepository
                        .counterUtilization(windowFrom, windowTo)
                        .stream()
                        .map(row -> new AnalyticsOverviewResponse
                                .CounterUtilizationResponse(
                                ((Number) row[0]).longValue(),
                                (String) row[1],
                                ((Number) row[2]).longValue()
                        ))
                        .toList();

        List<AnalyticsOverviewResponse.HourlyDistributionResponse> hourly =
                queueEntryRepository
                        .hourlyDistribution(windowFrom, windowTo)
                        .stream()
                        .map(row -> new AnalyticsOverviewResponse
                                .HourlyDistributionResponse(
                                ((Number) row[0]).intValue(),
                                ((Number) row[1]).longValue()
                        ))
                        .toList();

        return new AnalyticsOverviewResponse(
                windowFrom,
                windowTo,
                served,
                cancelled,
                avgWait,
                avgActual,
                avgPredicted,
                mae,
                waitMae,
                meanAbandonmentRisk,
                counters,
                hourly
        );
    }
}
