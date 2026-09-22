package com.smartqueue.dto;

import com.smartqueue.model.PaymentMethod;
import com.smartqueue.model.PriorityType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JoinQueueRequest(

        @NotBlank(message = "Customer name is required")
        @Size(max = 60, message = "Customer name is too long")
        String customerName,

        @Min(value = 1, message = "Basket must contain at least one item")
        @Max(value = 300, message = "Basket cannot exceed 300 items")
        int itemCount,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        @NotNull(message = "Priority type is required")
        PriorityType priorityType

) {
}