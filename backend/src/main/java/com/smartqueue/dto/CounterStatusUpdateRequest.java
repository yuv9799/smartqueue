package com.smartqueue.dto;

import com.smartqueue.model.CounterStatus;
import jakarta.validation.constraints.NotNull;

public record CounterStatusUpdateRequest(

        @NotNull(message = "Counter status is required")
        CounterStatus status

) {
}