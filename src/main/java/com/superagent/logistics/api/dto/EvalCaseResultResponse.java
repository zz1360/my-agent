package com.superagent.logistics.api.dto;

import java.time.Instant;

public record EvalCaseResultResponse(
        String caseId,
        boolean passed,
        String traceId,
        String riskLevel,
        long latencyMs,
        String failureReason,
        String responseExcerpt,
        Instant createdAt
) {
}
