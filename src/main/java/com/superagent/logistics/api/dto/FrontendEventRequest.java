package com.superagent.logistics.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record FrontendEventRequest(
        @NotBlank @Size(max = 40) String type,
        @Size(max = 160) String route,
        @Size(max = 500) String message,
        @Min(0) @Max(599) Integer status,
        @Min(0) @Max(600000) Long durationMs,
        @Size(max = 80) String traceId,
        Instant timestamp
) {
}
