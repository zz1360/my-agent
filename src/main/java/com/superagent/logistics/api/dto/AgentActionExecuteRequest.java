package com.superagent.logistics.api.dto;

import java.util.List;

public record AgentActionExecuteRequest(
        String tenantId,
        String userId,
        List<String> roles,
        Boolean force,
        String comment,
        String idempotencyKey,
        Boolean simulateFailure
) {
}
