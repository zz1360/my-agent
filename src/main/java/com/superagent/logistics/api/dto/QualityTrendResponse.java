package com.superagent.logistics.api.dto;

import java.time.LocalDate;
import java.util.List;

public record QualityTrendResponse(
        LocalDate fromDate,
        LocalDate toDate,
        List<DailyQualityPoint> dailyTrends,
        List<MetricCount> alertStatusCounts,
        List<MetricCount> taskStatusCounts
) {
    public record DailyQualityPoint(
            LocalDate date,
            long openedAlerts,
            long resolvedAlerts,
            long createdTasks,
            long completedTasks
    ) {
    }

    public record MetricCount(String name, long count) {
    }
}
