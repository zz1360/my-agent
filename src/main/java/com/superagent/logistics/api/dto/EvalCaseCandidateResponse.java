package com.superagent.logistics.api.dto;

import java.time.Instant;
import java.util.List;

public record EvalCaseCandidateResponse(
        String tenantId,
        String candidateId,
        String feedbackId,
        String conversationId,
        String messageId,
        String traceId,
        String sourceQuestion,
        String sourceAnswer,
        String rating,
        String reason,
        String endpoint,
        String evalType,
        List<String> expectedContains,
        List<String> expectedCitations,
        List<String> expectedRagDocIds,
        List<String> expectedRagChunkIds,
        int expectedMinToolCalls,
        int expectedTopK,
        String ragQuery,
        String status,
        String evalCaseId,
        String ragExperimentId,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
