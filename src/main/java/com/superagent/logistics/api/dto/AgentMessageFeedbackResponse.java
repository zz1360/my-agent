package com.superagent.logistics.api.dto;

import java.time.Instant;

public record AgentMessageFeedbackResponse(
        String feedbackId,
        String tenantId,
        String conversationId,
        String messageId,
        String traceId,
        String userId,
        String rating,
        String reason,
        String comment,
        Instant createdAt
) {
}
