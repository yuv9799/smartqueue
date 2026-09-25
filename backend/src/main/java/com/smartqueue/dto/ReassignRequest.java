package com.smartqueue.dto;

import jakarta.validation.constraints.NotNull;

public record ReassignRequest(
        @NotNull(message = "newCounterId is required")
        Long newCounterId
) {}