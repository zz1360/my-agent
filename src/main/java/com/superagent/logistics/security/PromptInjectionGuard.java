package com.superagent.logistics.security;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptInjectionGuard {

    private static final List<String> SUSPICIOUS_PHRASES = List.of(
            "忽略之前",
            "忽略以上",
            "ignore previous",
            "system prompt",
            "开发者消息",
            "越权",
            "绕过权限"
    );

    public boolean isSuspicious(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return SUSPICIOUS_PHRASES.stream().anyMatch(normalized::contains);
    }
}
