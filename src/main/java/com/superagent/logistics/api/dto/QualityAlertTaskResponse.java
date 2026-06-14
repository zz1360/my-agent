package com.superagent.logistics.api.dto;

import java.time.Instant;

public record QualityAlertTaskResponse(
        String alertId,
        String taskId,
        String actionId,
        String status,
        Instant createdAt
) {
}
