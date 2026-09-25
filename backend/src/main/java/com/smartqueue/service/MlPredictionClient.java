package com.smartqueue.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.Duration;

/**
 * Calls the SmartQueue ML sidecar ({@code ml-service/}) for checkout-time,
 * waiting-time, and abandonment predictions.
 *
 * <p>Timeouts are kept intentionally tight (2 s) so that the ML service
 * being unavailable never blocks a queue-join.  The caller falls back to
 * {@link CheckoutTimePredictor} when this client returns {@code null}.
 *
 * <p>Environment / property: {@code smartqueue.ml.base-url}
 * (default {@value #DEFAULT_BASE_URL}).
 */
@Service
public class MlPredictionClient {

    private static final Logger log = LoggerFactory.getLogger(MlPredictionClient.class);
    static final String DEFAULT_BASE_URL = "http://localhost:8001";

    private final RestClient restClient;

    public MlPredictionClient(
            @Value("${smartqueue.ml.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Requests ML predictions from the sidecar.
     *
     * @return a {@link MlPrediction} on success; {@code null} if the service
     *         is unreachable or returns an error (caller should fall back to
     *         {@link CheckoutTimePredictor}).
     */
    public MlPrediction predict(MlFeatures features) {
        try {
            MlResponse response = restClient.post()
                    .uri("/predict")
                    .body(new MlRequest(
                            features.basketItems(),
                            features.paymentMethod(),
                            features.assistanceRequired(),
                            features.openCounters(),
                            features.queueLength(),
                            features.queuePosition(),
                            features.counterLoadMinutes(),
                            features.hour(),
                            features.dayOfWeek(),
                            features.isWeekend(),
                            features.isPeak()
                    ))
                    .retrieve()
                    .body(MlResponse.class);

            if (response == null) {
                log.warn("ML service returned null body");
                return null;
            }

            return new MlPrediction(
                    (int) Math.round(response.service_minutes() * 60.0),
                    (long) Math.round(response.wait_minutes() * 60.0),
                    response.abandonment_probability(),
                    response.model_source()
            );

        } catch (Exception exc) {
            log.warn("ML service call failed — using rule-based fallback: {}",
                    exc.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /** All features required by the ML sidecar. */
    public record MlFeatures(
            int basketItems,
            String paymentMethod,   // UPI | CARD | CASH
            int assistanceRequired, // 0 | 1
            int openCounters,
            int queueLength,
            int queuePosition,
            double counterLoadMinutes,
            int hour,
            int dayOfWeek,   // 1=Mon .. 7=Sun (Java LocalDateTime ISO standard)
            int isWeekend,   // 0 | 1
            int isPeak       // 0 | 1
    ) {
        public static MlFeatures fromJoinParams(
                int basketItems,
                String paymentMethod,
                int assistanceRequired,
                int openCounters,
                int queueLength,
                int queuePosition,
                double counterLoadMinutes) {

            LocalDateTime now = LocalDateTime.now();
            int hour = now.getHour();
            int dayOfWeek = now.getDayOfWeek().getValue(); // 1=Mon..7=Sun (ISO)
            boolean isWeekend = now.getDayOfWeek() == DayOfWeek.SATURDAY
                    || now.getDayOfWeek() == DayOfWeek.SUNDAY;
            boolean isPeak = (hour >= 12 && hour <= 13) || (hour >= 17 && hour <= 20);

            return new MlFeatures(
                    basketItems,
                    paymentMethod,
                    assistanceRequired,
                    openCounters,
                    queueLength,
                    queuePosition,
                    counterLoadMinutes,
                    hour,
                    dayOfWeek,
                    isWeekend ? 1 : 0,
                    isPeak ? 1 : 0
            );
        }
    }

    /** Prediction returned by the ML sidecar. */
    public record MlPrediction(
            int serviceSeconds,
            long waitSeconds,
            double abandonmentProbability,
            String source
    ) {}

    // -------------------------------------------------------------------------
    // DTOs used for JSON serialisation (matches ml_service/service.py)
    // -------------------------------------------------------------------------

    record MlRequest(
            int basket_items,
            String payment_method,
            int assistance_required,
            int open_counters,
            int queue_length,
            int queue_position,
            double counter_load_minutes,
            int hour,
            int day_of_week,
            int is_weekend,
            int is_peak
    ) {}

    record MlResponse(
            double service_minutes,
            double wait_minutes,
            double abandonment_probability,
            String model_source
    ) {}
}