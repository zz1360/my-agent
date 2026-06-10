package com.superagent.logistics.api.dto;

import java.time.LocalDate;
import java.util.List;

public record AgentActionExecutionMetricsResponse(
        String tenantId,
        LocalDate from,
        LocalDate to,
        long totalExecutions,
        long successCount,
        long failedCount,
        long retryableFailedCount,
        double successRate,
        double failureRate,
        List<AgentActionExecutionMetricRow> byActionType,
        List<AgentActionExecutionMetricRow> byExecutor,
        List<AgentActionExecutionMetricRow> byTargetSystem
) {
}
