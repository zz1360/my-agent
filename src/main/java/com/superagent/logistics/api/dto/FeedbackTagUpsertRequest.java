package com.superagent.logistics.api.dto;

import java.util.List;

public record FeedbackTagUpsertRequest(
        String tenantId,
        String userId,
        List<String> roles,
        String tagName,
        String category,
        String description,
        Boolean enabled,
        Integer sortOrder
) {
}
