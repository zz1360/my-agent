package com.superagent.logistics.api.dto;

import java.time.Instant;

public record FeedbackCandidateAuditResponse(
        String auditId,
        String tenantId,
        String candidateId,
        String feedbackId,
        String actionType,
        String actorId,
        String reviewStatus,
        String summary,
        String detailJson,
        Instant createdAt
) {
}
