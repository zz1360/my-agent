package com.superagent.logistics.api.dto;

import java.util.List;

public record QualityAlertEvaluationResponse(
        int evaluatedRules,
        int openAlerts,
        int resolvedAlerts,
        List<QualityAlertResponse> alerts
) {
}
