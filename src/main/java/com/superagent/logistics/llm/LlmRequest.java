package com.superagent.logistics.llm;

import java.util.Map;

public record LlmRequest(
        String traceId,
        String tenantId,
        String userId,
        String conversationId,
        String routeKey,
        String scene,
        String prompt,
        Object[] tools,
        Map<String, Object> toolContext
) {
    public LlmRequest {
        tools = tools == null ? new Object[0] : tools;
        toolContext = toolContext == null ? Map.of() : Map.copyOf(toolContext);
    }
}
