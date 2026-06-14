package com.superagent.logistics.api.dto;

import java.util.List;

public record EvalCaseCandidateReviewRequest(
        String tenantId,
        String userId,
        List<String> roles,
        String reviewStatus,
        String comment
) {
}
