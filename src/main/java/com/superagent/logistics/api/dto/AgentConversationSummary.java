package com.superagent.logistics.api.dto;

import java.time.Instant;

public record AgentConversationSummary(
        String tenantId,
        String conversationId,
        String userId,
        String title,
        String lastMessage,
        String lastTraceId,
        String lastRiskLevel,
        int messageCount,
        Instant createdAt,
        Instant updatedAt
) {
}
