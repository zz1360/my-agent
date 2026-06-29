package com.superagent.logistics.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ModelCallLogResponse(
        String traceId,
        String tenantId,
        String userId,
        String conversationId,
        String scene,
        String routeKey,
        String provider,
        String model,
        boolean streaming,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        boolean usageEstimated,
        BigDecimal inputCost,
        BigDecimal outputCost,
        BigDecimal totalCost,
        String currency,
        long latencyMs,
        Long ttftMs,
        String status,
        String errorCode,
        String errorMessage,
        String fallbackFrom,
        String fallbackReason,
        Instant createdAt
) {
}
