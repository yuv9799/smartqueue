package com.smartqueue.dto;

import com.smartqueue.model.PaymentMethod;
import com.smartqueue.model.PriorityType;
import com.smartqueue.model.QueueEntry;
import com.smartqueue.model.QueueStatus;

import java.time.LocalDateTime;

public record QueueAssignmentResponse(
        Long id,
        String token,
        String customerName,
        int itemCount,
        PaymentMethod paymentMethod,
        PriorityType priorityType,
        QueueStatus status,
        int predictedServiceSeconds,
        String predictionSource,
        Long counterId,
        String counterName,
        long estimatedWaitSeconds,
        Double abandonmentRisk,
        LocalDateTime arrivalTime
) {

    public static QueueAssignmentResponse from(
            QueueEntry entry,
            long estimatedWaitSeconds,
            Double abandonmentRisk) {

        return new QueueAssignmentResponse(
                entry.getId(),
                entry.getToken(),
                entry.getCustomerName(),
                entry.getItemCount(),
                entry.getPaymentMethod(),
                entry.getPriorityType(),
                entry.getStatus(),
                entry.getPredictedServiceSeconds(),
                entry.getPredictionSource(),
                entry.getCounter().getId(),
                entry.getCounter().getName(),
                estimatedWaitSeconds,
                abandonmentRisk,
                entry.getArrivalTime()
        );
    }
}
