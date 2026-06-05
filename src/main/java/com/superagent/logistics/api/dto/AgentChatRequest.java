package com.superagent.logistics.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AgentChatRequest(
        String conversationId,
        String userId,
        String tenantId,
        List<String> roles,
        @NotBlank String message,
        Boolean returnCitations
) {
}
