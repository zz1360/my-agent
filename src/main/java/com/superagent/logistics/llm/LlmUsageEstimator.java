package com.superagent.logistics.llm;

import org.springframework.stereotype.Component;

@Component
public class LlmUsageEstimator {

    public LlmUsage estimate(String prompt, String completion) {
        int promptTokens = estimateTokens(prompt);
        int completionTokens = estimateTokens(completion);
        return new LlmUsage(promptTokens, completionTokens, promptTokens + completionTokens, true);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String compact = text.replaceAll("\\s+", "");
        long cjk = compact.codePoints()
                .filter(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                .count();
        int nonCjk = Math.max(0, compact.length() - (int) cjk);
        return Math.max(1, (int) Math.ceil(cjk * 1.1 + nonCjk / 3.8));
    }
}
