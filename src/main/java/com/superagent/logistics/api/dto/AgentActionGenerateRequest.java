package com.superagent.logistics.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

public record AgentActionGenerateRequest(
        String tenantId,
        String userId,
        List<String> roles,
        String traceId,
        String conversationId,
        @NotBlank String customerId,
        LocalDate from,
        LocalDate to,
        Integer days
) {
}
