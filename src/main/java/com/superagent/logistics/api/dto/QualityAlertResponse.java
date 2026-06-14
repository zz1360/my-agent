package com.superagent.logistics.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record QualityAlertResponse(
        String alertId,
        String ruleId,
        String metricType,
        String severity,
        String status,
        BigDecimal metricValue,
        BigDecimal thresholdValue,
        int windowDays,
        String summary,
        String detailJson,
        Instant firstTriggeredAt,
        Instant lastTriggeredAt,
        Instant resolvedAt,
        String taskId,
        Instant taskCreatedAt
) {
}
