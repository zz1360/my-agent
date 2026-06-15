package com.superagent.logistics.api.dto;

import java.time.Instant;
import java.util.List;

public record AuditResponse(
        String traceId,
        String tenantId,
        String userId,
        String conversationId,
        String userMessage,
        String finalAnswer,
        String riskLevel,
        long latencyMs,
        Instant createdAt,
        List<ToolCallSummary> toolCalls,
        List<RagAuditResponse> ragAudits
) {
}
