package com.superagent.logistics.api.dto;

import java.math.BigDecimal;

public record EvalReleaseGateRequest(
        String tenantId,
        String suiteId,
        String modelVersion,
        String knowledgeVersion,
        String promptVersion,
        BigDecimal minPassRate,
        Integer maxRegressions
) {
}
