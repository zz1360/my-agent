package com.superagent.logistics.api.dto;

import java.util.List;

public record AgentActionAutomationResponse(
        String tenantId,
        String customerId,
        int scanned,
        int executed,
        int skipped,
        List<AgentActionExecutionResponse> executions
) {
}
