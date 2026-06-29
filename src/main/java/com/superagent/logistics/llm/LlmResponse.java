package com.superagent.logistics.llm;

public record LlmResponse(
        String provider,
        String model,
        String routeKey,
        String content,
        LlmUsage usage,
        long latencyMs,
        Long ttftMs,
        boolean streaming
) {
}
