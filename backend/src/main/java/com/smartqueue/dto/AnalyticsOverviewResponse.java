package com.smartqueue.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Top-level analytics response covering an optional time window.
 */
public record AnalyticsOverviewResponse(

        /** Window start used for this query. */
        LocalDateTime from,

        /** Window end used for this query. */
        LocalDateTime to,

        /** Total customers who completed checkout. */
        long totalServed,

        /** Total customers who cancelled before being served. */
        long totalCancelled,

        /**
         * Average time a customer waited before service started (seconds).
         * Computed as the span between arrivalTime and serviceStartedAt for
         * completed customers.
         */
        Double averageWaitSeconds,

        /** Average actual service duration for completed customers (seconds). */
        Double averageActualServiceSeconds,

        /** Average predicted service duration for completed customers (seconds). */
        Double averagePredictedServiceSeconds,

        /** Mean absolute error of predictions for completed customers (seconds). */
        Double predictionMAESeconds,

        /**
         * Mean absolute error of ML-predicted wait times vs actual wait times
         * for completed customers (seconds). Computed only for entries where
         * both estimatedWaitSeconds and actualWaitSeconds are available.
         */
        Double waitPredictionMAESeconds,

        /**
         * Mean predicted abandonment probability (0–1) across all customers in
         * the window, as predicted at join time by the ML model. Higher values
         * indicate customers the model flagged as likely to abandon.
         */
        Double meanPredictedAbandonmentRisk,

        /** Per-counter completion counts, ordered by counter ID. */
        List<CounterUtilizationResponse> counterUtilization,

        /** Hourly throughput for the window. */
        List<HourlyDistributionResponse> hourlyDistribution

) {

    public record CounterUtilizationResponse(
            Long counterId,
            String counterName,
            long completedCount
    ) {}

    public record HourlyDistributionResponse(
            int hour,
            long count
    ) {}
}
