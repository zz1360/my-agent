package com.superagent.logistics.api.dto;

import java.util.List;

public record EvalCaseCandidateCreateRequest(
        String tenantId,
        String userId,
        List<String> roles,
        String endpoint,
        String evalType,
        List<String> expectedContains,
        List<String> expectedCitations,
        List<String> expectedRagDocIds,
        List<String> expectedRagChunkIds,
        Integer expectedMinToolCalls,
        Integer expectedTopK,
        String ragQuery
) {
}
