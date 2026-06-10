package com.superagent.logistics.api.dto;

import java.util.List;

public record AgentActionAutomationRequest(
        String tenantId,
        String userId,
        List<String> roles,
        String customerId,
        Integer limit
) {
}
