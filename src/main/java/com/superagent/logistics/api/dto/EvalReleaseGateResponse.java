package com.superagent.logistics.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record EvalReleaseGateResponse(
        String gateId,
        String tenantId,
        String suiteId,
        String status,
        String candidateRunId,
        String baselineRunId,
        int totalCases,
        int passedCases,
        int failedCases,
        BigDecimal passRate,
        BigDecimal minPassRate,
        int regressedCases,
        int maxRegressions,
        List<String> reasons,
        Instant createdAt
) {
}
