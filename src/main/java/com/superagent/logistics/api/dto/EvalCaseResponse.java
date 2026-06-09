package com.superagent.logistics.api.dto;

import java.time.Instant;
import java.util.List;

public record EvalCaseResponse(
        String caseId,
        String name,
        String endpoint,
        String evalType,
        String tenantId,
        String userId,
        List<String> roles,
        List<String> expectedContains,
        List<String> expectedCitations,
        List<String> expectedRagDocIds,
        List<String> expectedRagChunkIds,
        int expectedMinToolCalls,
        int expectedTopK,
        String ragQuery,
        String riskLevel,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
