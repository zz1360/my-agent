package com.superagent.logistics.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AgentMessageFeedbackRequest(
        String tenantId,
        String userId,
        List<String> roles,
        String conversationId,
        String traceId,
        @NotBlank String rating,
        String reason,
        String comment
) {
}
