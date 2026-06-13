package com.superagent.logistics.api.dto;

import java.util.List;

public record EvalCaseCandidatePromoteRequest(
        String tenantId,
        String userId,
        List<String> roles,
        String caseId,
        String name,
        Boolean enabled,
        List<String> expectedContains,
        List<String> expectedCitations,
        List<String> expectedRagDocIds,
        List<String> expectedRagChunkIds,
        Integer expectedMinToolCalls,
        Integer expectedTopK,
        String ragQuery,
        String riskLevel
) {
}
