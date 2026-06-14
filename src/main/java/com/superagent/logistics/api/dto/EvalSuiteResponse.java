package com.superagent.logistics.api.dto;

import java.time.Instant;

public record EvalSuiteResponse(
        String suiteId,
        String suiteName,
        String suiteVersion,
        String description,
        boolean enabled,
        int caseCount,
        Instant createdAt,
        Instant updatedAt
) {
}
