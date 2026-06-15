package com.superagent.logistics.ops;

import com.superagent.logistics.api.dto.OpsMetricsSummaryResponse;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
public class OpsMetricsService {

    private final JdbcTemplate jdbcTemplate;

    public OpsMetricsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OpsMetricsSummaryResponse summary() {
        return new OpsMetricsSummaryResponse(
                totalQuestions(),
                decimal(avgAgentLatencyMs()),
                decimal(latestRagRecallAtK()),
                decimal(toolCallSuccessRate()),
                releaseGateCount("PASSED"),
                releaseGateCount("BLOCKED"),
                flywayVersion(),
                Instant.now()
        );
    }

    public long totalQuestions() {
        return longValue("SELECT COUNT(*) FROM ai_agent_message WHERE role = 'USER'");
    }

    public double avgAgentLatencyMs() {
        return doubleValue("SELECT AVG(latency_ms) FROM ai_agent_message WHERE role <> 'USER' AND latency_ms IS NOT NULL");
    }

    public double latestRagRecallAtK() {
        return doubleValue("SELECT AVG(rag_recall_at_k) FROM ai_eval_case_result WHERE rag_recall_at_k IS NOT NULL");
    }

    public double toolCallSuccessRate() {
        long total = longValue("SELECT COUNT(*) FROM ai_agent_tool_call");
        if (total == 0) {
            return 1.0;
        }
        long success = longValue("SELECT COUNT(*) FROM ai_agent_tool_call WHERE status = 'SUCCESS'");
        return success / (double) total;
    }

    public long releaseGatePassed() {
        return releaseGateCount("PASSED");
    }

    public long releaseGateBlocked() {
        return releaseGateCount("BLOCKED");
    }

    public String flywayVersion() {
        return jdbcTemplate.query("""
                SELECT version FROM flyway_schema_history
                WHERE success = true
                ORDER BY installed_rank DESC
                LIMIT 1
                """, rs -> rs.next() ? rs.getString("version") : "none");
    }

    private long releaseGateCount(String status) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ai_eval_release_gate
                WHERE status = ?
                """, Long.class, status);
        return value == null ? 0 : value;
    }

    private long longValue(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private double doubleValue(String sql) {
        Double value = jdbcTemplate.queryForObject(sql, Double.class);
        return value == null ? 0 : value;
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    @Configuration
    static class OpsMeterConfig {

        @Bean
        MeterBinder logisticsAgentMeters(OpsMetricsService metricsService) {
            return registry -> {
                Gauge.builder("logistics.agent.questions.total", metricsService, OpsMetricsService::totalQuestions)
                        .description("Total user questions persisted by the logistics agent")
                        .register(registry);
                Gauge.builder("logistics.agent.latency.avg.ms", metricsService, OpsMetricsService::avgAgentLatencyMs)
                        .description("Average assistant message latency in milliseconds")
                        .register(registry);
                Gauge.builder("logistics.agent.rag.recall.latest", metricsService, OpsMetricsService::latestRagRecallAtK)
                        .description("Average RAG recall@k from stored eval case results")
                        .register(registry);
                Gauge.builder("logistics.agent.tool.success.rate", metricsService, OpsMetricsService::toolCallSuccessRate)
                        .description("Tool call success rate")
                        .register(registry);
                Gauge.builder("logistics.agent.release_gate.passed.total", metricsService, OpsMetricsService::releaseGatePassed)
                        .description("Passed release gate count")
                        .register(registry);
                Gauge.builder("logistics.agent.release_gate.blocked.total", metricsService, OpsMetricsService::releaseGateBlocked)
                        .description("Blocked release gate count")
                        .register(registry);
            };
        }
    }
}
