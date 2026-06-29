package com.superagent.logistics.llm;

public record ModelCallLogEntry(
        String traceId,
        String tenantId,
        String userId,
        String conversationId,
        String scene,
        String routeKey,
        String provider,
        String model,
        boolean streaming,
        LlmUsage usage,
        long latencyMs,
        Long ttftMs,
        String status,
        String errorCode,
        String errorMessage,
        String fallbackFrom,
        String fallbackReason
) {
}
