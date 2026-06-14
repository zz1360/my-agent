package com.superagent.logistics.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record QualityAlertRuleUpsertRequest(
        String tenantId,
        String userId,
        List<String> roles,
        String ruleName,
        String metricType,
        BigDecimal thresholdValue,
        Integer windowDays,
        String severity,
        Boolean enabled,
        String description
) {
}
