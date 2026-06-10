package com.superagent.logistics.api.dto;

import java.time.Instant;

public record AgentActionBusinessLinkResponse(
        String tenantId,
        String actionId,
        String actionType,
        String businessTable,
        String businessId,
        String customerId,
        String waybillId,
        String status,
        Instant createdAt,
        String traceId,
        String latestExecutionId
) {
}
