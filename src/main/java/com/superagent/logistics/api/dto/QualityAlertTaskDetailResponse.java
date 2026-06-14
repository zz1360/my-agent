package com.superagent.logistics.api.dto;

import java.time.Instant;

public record QualityAlertTaskDetailResponse(
        String alertId,
        String taskId,
        String actionId,
        String title,
        String description,
        String ownerRole,
        String ownerUserId,
        String status,
        String lastComment,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
