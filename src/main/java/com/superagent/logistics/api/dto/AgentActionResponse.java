package com.superagent.logistics.api.dto;

import java.time.Instant;

public record AgentActionResponse(
        String tenantId,
        String actionId,
        String traceId,
        String conversationId,
        String customerId,
        String waybillId,
        String actionType,
        String title,
        String priority,
        String riskLevel,
        String status,
        String draftContent,
        String evidenceJson,
        String createdBy,
        String reviewerId,
        String reviewComment,
        Instant createdAt,
        Instant updatedAt,
        Instant reviewedAt
) {
}
