package com.superagent.logistics.api.dto;

public record ToolCallSummary(
        String tool,
        String status,
        String summary,
        long latencyMs,
        String errorCode
) {
}
