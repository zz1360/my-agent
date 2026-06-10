package com.superagent.logistics.api.dto;

import java.time.Instant;

public record AgentActionExecutionResponse(
        String tenantId,
        String executionId,
        String actionId,
        String actionType,
        String executorName,
        String targetSystem,
        String externalRefId,
        String idempotencyKey,
        boolean lowRisk,
        String status,
        String requestJson,
        String responseJson,
        String failureReason,
        int retryCount,
        int maxRetryCount,
        Instant nextRetryAt,
        String executedBy,
        Instant startedAt,
        Instant finishedAt
) {
}
