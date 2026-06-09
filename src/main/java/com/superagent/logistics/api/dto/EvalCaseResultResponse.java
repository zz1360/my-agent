package com.superagent.logistics.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record EvalCaseResultResponse(
        String caseId,
        boolean passed,
        String traceId,
        String riskLevel,
        long latencyMs,
        String failureReason,
        String responseExcerpt,
        BigDecimal ragHitRate,
        BigDecimal ragRecallAtK,
        BigDecimal ragPrecisionAtK,
        BigDecimal ragMrr,
        BigDecimal ragNdcg,
        Integer ragExpectedTotal,
        Integer ragHitCount,
        List<String> ragTopDocIds,
        List<String> ragTopChunkIds,
        String ragMetricsJson,
        Instant createdAt
) {
}
