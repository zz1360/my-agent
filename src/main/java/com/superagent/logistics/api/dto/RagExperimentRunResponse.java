package com.superagent.logistics.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RagExperimentRunResponse(
        String tenantId,
        String runId,
        String experimentId,
        String mode,
        String status,
        BigDecimal recallAtK,
        BigDecimal precisionAtK,
        BigDecimal mrr,
        BigDecimal ndcg,
        int hitCount,
        int expectedTotal,
        long latencyMs,
        List<String> topDocIds,
        List<String> topChunkIds,
        String metricsJson,
        Instant createdAt
) {
}
