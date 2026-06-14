package com.superagent.logistics.api.dto;

import java.time.Instant;
import java.util.List;

public record EvalRunResponse(
        String runId,
        String tenantId,
        String suiteId,
        String suiteVersion,
        String status,
        int totalCases,
        int passedCases,
        int failedCases,
        String modelProvider,
        String modelVersion,
        String knowledgeVersion,
        String promptVersion,
        Instant startedAt,
        Instant finishedAt,
        List<EvalCaseResultResponse> results
) {
}
