package com.superagent.logistics.api;

import com.superagent.logistics.api.dto.OpsMetricsSummaryResponse;
import com.superagent.logistics.api.dto.OpsReadinessResponse;
import com.superagent.logistics.ops.OpsMetricsService;
import com.superagent.logistics.ops.OpsReadinessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ops")
public class OpsController {

    private final OpsReadinessService readinessService;
    private final OpsMetricsService metricsService;

    public OpsController(OpsReadinessService readinessService, OpsMetricsService metricsService) {
        this.readinessService = readinessService;
        this.metricsService = metricsService;
    }

    @GetMapping("/readiness")
    public OpsReadinessResponse readiness() {
        return readinessService.readiness();
    }

    @GetMapping("/metrics/summary")
    public OpsMetricsSummaryResponse metricsSummary() {
        return metricsService.summary();
    }
}
