package com.superagent.logistics.api.dto;

import java.time.Instant;

public record FeedbackTagResponse(
        String tagCode,
        String tagName,
        String category,
        String description,
        boolean enabled,
        int sortOrder,
        Instant updatedAt
) {
}
