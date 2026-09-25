package com.smartqueue.dto;

import com.smartqueue.model.PaymentMethod;
import com.smartqueue.model.PriorityType;
import com.smartqueue.model.QueueEntry;
import com.smartqueue.model.QueueStatus;

import java.time.LocalDateTime;

public record QueueEntryResponse(
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
        LocalDateTime arrivalTime,
        LocalDateTime serviceStartedAt,
        LocalDateTime serviceCompletedAt,
        Integer actualServiceSeconds,
        LocalDateTime cancelledAt
) {

    public static QueueEntryResponse from(QueueEntry entry) {
        return new QueueEntryResponse(
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
                entry.getArrivalTime(),
                entry.getServiceStartedAt(),
                entry.getServiceCompletedAt(),
                entry.getActualServiceSeconds(),
                entry.getCancelledAt()
        );
    }
}