package com.superagent.logistics.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record EvalRunComparisonResponse(
        String baselineRunId,
        String candidateRunId,
        String baselineVersion,
        String candidateVersion,
        int totalCases,
        int unchangedCases,
        int improvedCases,
        int regressedCases,
        int newCases,
        int removedCases,
        List<EvalCaseComparison> cases
) {
    public record EvalCaseComparison(
            String caseId,
            String changeType,
            Boolean baselinePassed,
            Boolean candidatePassed,
            String baselineFailureReason,
            String candidateFailureReason,
            BigDecimal baselineRagRecallAtK,
            BigDecimal candidateRagRecallAtK,
            Long latencyDeltaMs
    ) {
    }
}
