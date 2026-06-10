package com.superagent.logistics.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AgentActionReviewRequest(
        String tenantId,
        String userId,
        List<String> roles,
        @NotBlank String status,
        String comment
) {
}
