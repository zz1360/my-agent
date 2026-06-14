package com.superagent.logistics.api.dto;

import java.util.List;

public record FeedbackQualityMetricsResponse(
        long totalFeedback,
        long helpfulFeedback,
        long notHelpfulFeedback,
        double negativeRate,
        long candidateCount,
        long approvedCandidates,
        long rejectedCandidates,
        long convertedEvalCases,
        long ragExperimentCandidates,
        double candidateConversionRate,
        double approvedCandidateRate,
        double ragExperimentPassRate,
        double evalPassRate,
        List<MetricCount> byReason,
        List<MetricCount> byTag,
        List<MetricCount> byReviewStatus,
        List<MetricCount> ragExperimentStatus,
        List<MetricCount> evalRunStatus
) {
    public record MetricCount(String name, long count) {
    }
}
