package com.superagent.logistics.api.dto;

public record AgentActionExecutionMetricRow(
        String name,
        long total,
        long successCount,
        long failedCount,
        double successRate
) {
}
