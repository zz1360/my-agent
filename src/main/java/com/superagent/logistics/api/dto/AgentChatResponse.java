package com.superagent.logistics.api.dto;

import java.time.Instant;
import java.util.List;

public record AgentChatResponse(
        String traceId,
        String conversationId,
        String answer,
        String riskLevel,
        double confidence,
        List<Citation> citations,
        List<ToolCallSummary> toolCalls,
        Instant createdAt
) {
}
