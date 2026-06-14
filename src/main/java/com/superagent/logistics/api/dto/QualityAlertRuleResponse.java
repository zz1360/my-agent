package com.superagent.logistics.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record QualityAlertRuleResponse(
        String ruleId,
        String ruleName,
        String metricType,
        BigDecimal thresholdValue,
        int windowDays,
        String severity,
        boolean enabled,
        String description,
        Instant updatedAt
) {
}
