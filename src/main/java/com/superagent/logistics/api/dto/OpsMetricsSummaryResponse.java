package com.superagent.logistics.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OpsMetricsSummaryResponse(
        long totalQuestions,
        BigDecimal averageAgentLatencyMs,
        BigDecimal latestRagRecallAtK,
        BigDecimal toolCallSuccessRate,
        long releaseGatePassed,
        long releaseGateBlocked,
        String flywayVersion,
        Instant measuredAt
) {
}
