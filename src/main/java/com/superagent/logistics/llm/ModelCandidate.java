package com.superagent.logistics.llm;

public record ModelCandidate(
        String modelId,
        String provider,
        String model,
        boolean enabled,
        boolean supportsStreaming,
        boolean supportsToolCalling,
        long timeoutMs,
        Integer maxPromptTokens,
        Integer maxCompletionTokens,
        Double temperature
) {
}
