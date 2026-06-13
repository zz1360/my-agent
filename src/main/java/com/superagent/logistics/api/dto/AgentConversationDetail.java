package com.superagent.logistics.api.dto;

import java.time.Instant;
import java.util.List;

public record AgentConversationDetail(
        String tenantId,
        String conversationId,
        String userId,
        String title,
        String lastMessage,
        String lastTraceId,
        String lastRiskLevel,
        int messageCount,
        Instant createdAt,
        Instant updatedAt,
        List<AgentMessageResponse> messages
) {
}
