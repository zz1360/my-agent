package com.superagent.logistics.api.dto;

import java.time.Instant;

public record ApiErrorResponse(
        String code,
        String message,
        int status,
        String path,
        String traceId,
        Instant timestamp
) {
}
