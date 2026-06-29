package com.superagent.logistics.llm;

public record LlmUsage(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        boolean estimated
) {
    public static LlmUsage empty() {
        return new LlmUsage(null, null, null, false);
    }
}
