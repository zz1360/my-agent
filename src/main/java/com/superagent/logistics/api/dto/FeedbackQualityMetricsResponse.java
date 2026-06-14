package com.superagent.logistics.api.dto;

import java.time.LocalDate;
import java.util.List;

public record FeedbackQualityMetricsResponse(
        LocalDate fromDate,
        LocalDate toDate,
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
        List<MetricCount> evalRunStatus,
        List<DailyQualityTrend> dailyTrends
) {
    public record MetricCount(String name, long count) {
    }

    public record DailyQualityTrend(
            LocalDate date,
            long totalFeedback,
            long notHelpfulFeedback,
            long candidateCount,
            long approvedCandidates,
            long ragExperimentCandidates,
            double negativeRate,
            double candidateConversionRate,
            double approvedCandidateRate
    ) {
    }
}
