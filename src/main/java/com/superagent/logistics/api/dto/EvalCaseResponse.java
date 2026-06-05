package com.superagent.logistics.api.dto;

import java.time.Instant;
import java.util.List;

public record EvalCaseResponse(
        String caseId,
        String name,
        String endpoint,
        String tenantId,
        String userId,
        List<String> roles,
        List<String> expectedContains,
        List<String> expectedCitations,
        int expectedMinToolCalls,
        String riskLevel,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
