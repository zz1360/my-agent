package com.superagent.logistics.api.dto;

import java.math.BigDecimal;

public record ModelUsageSummaryResponse(
        String tenantId,
        long totalCalls,
        long successCalls,
        long failedCalls,
        long streamingCalls,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        BigDecimal totalCost,
        String currency,
        double avgLatencyMs,
        double avgTtftMs
) {
}
