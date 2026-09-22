package com.smartqueue.service;

import com.smartqueue.model.PaymentMethod;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CheckoutTimePredictor {
    public Prediction predict(
            int itemCount,
            PaymentMethod paymentMethod) {

        int baseSeconds = 32;
        int itemProcessingSeconds = itemCount * 5;

        int paymentSeconds = switch (paymentMethod) {
            case UPI -> 8;
            case CARD -> 15;
            case CASH -> 25;
        };

        int currentHour = LocalDateTime.now().getHour();

        boolean isPeakHour =
                currentHour == 12 ||
                        currentHour == 13 ||
                        currentHour >= 17 && currentHour <= 20;

        int peakHourSeconds = isPeakHour ? 14 : 0;

        int prediction =
                baseSeconds +
                        itemProcessingSeconds +
                        paymentSeconds +
                        peakHourSeconds;

        int limitedPrediction =
                Math.max(45, Math.min(prediction, 900));

        return new Prediction(
                limitedPrediction,
                "RULE_BASELINE_V1"
        );
    }

    public record Prediction(
            int seconds,
            String source) {
    }
}
