package com.superagent.logistics.api.dto;

import java.time.Instant;
import java.util.List;

public record AgentMessageResponse(
        String messageId,
        String role,
        String content,
        String traceId,
        String riskLevel,
        Double confidence,
        List<Citation> citations,
        List<ToolCallSummary> toolCalls,
        Instant createdAt
) {
}
