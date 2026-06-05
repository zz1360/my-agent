package com.superagent.logistics.api.dto;

import java.time.Instant;
import java.util.List;

public record EvalRunResponse(
        String runId,
        String tenantId,
        String status,
        int totalCases,
        int passedCases,
        int failedCases,
        String modelProvider,
        Instant startedAt,
        Instant finishedAt,
        List<EvalCaseResultResponse> results
) {
}
