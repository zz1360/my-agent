package com.superagent.logistics.llm;

import com.superagent.logistics.api.dto.ModelCallLogResponse;
import com.superagent.logistics.api.dto.ModelUsageSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ModelCallLogService {

    private static final String DEFAULT_CURRENCY = "CNY";

    private final JdbcTemplate jdbcTemplate;

    public ModelCallLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(ModelCallLogEntry entry) {
        LlmUsage usage = entry.usage() == null ? LlmUsage.empty() : entry.usage();
        ModelPrice price = priceFor(entry.provider(), entry.model());
        BigDecimal inputCost = cost(usage.promptTokens(), price.inputPricePer1mTokens());
        BigDecimal outputCost = cost(usage.completionTokens(), price.outputPricePer1mTokens());
        BigDecimal totalCost = inputCost.add(outputCost).setScale(8, RoundingMode.HALF_UP);
        jdbcTemplate.update("""
                INSERT INTO ai_model_call_log
                (trace_id, tenant_id, user_id, conversation_id, scene, route_key, provider, model, streaming,
                 prompt_tokens, completion_tokens, total_tokens, usage_estimated, input_cost, output_cost, total_cost,
                 currency, latency_ms, ttft_ms, status, error_code, error_message, fallback_from, fallback_reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                entry.traceId(),
                entry.tenantId(),
                entry.userId(),
                entry.conversationId(),
                entry.scene(),
                entry.routeKey(),
                entry.provider(),
                entry.model(),
                entry.streaming(),
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                usage.estimated(),
                inputCost,
                outputCost,
                totalCost,
                price.currency(),
                entry.latencyMs(),
                entry.ttftMs(),
                entry.status(),
                entry.errorCode(),
                truncate(entry.errorMessage(), 1000),
                entry.fallbackFrom(),
                entry.fallbackReason(),
                Timestamp.from(Instant.now()));
    }

    public List<ModelCallLogResponse> list(String tenantId, String traceId, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM ai_model_call_log
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (hasText(tenantId)) {
            sql.append(" AND tenant_id = ?");
            args.add(tenantId);
        }
        if (hasText(traceId)) {
            sql.append(" AND trace_id = ?");
            args.add(traceId);
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 200)));
        return jdbcTemplate.query(sql.toString(), this::mapLog, args.toArray());
    }

    public ModelUsageSummaryResponse summary(String tenantId, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(MAX(tenant_id), ?) AS tenant_id,
                       COUNT(*) AS total_calls,
                       SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS success_calls,
                       SUM(CASE WHEN status <> 'SUCCESS' THEN 1 ELSE 0 END) AS failed_calls,
                       SUM(CASE WHEN streaming = 1 THEN 1 ELSE 0 END) AS streaming_calls,
                       COALESCE(SUM(prompt_tokens), 0) AS prompt_tokens,
                       COALESCE(SUM(completion_tokens), 0) AS completion_tokens,
                       COALESCE(SUM(total_tokens), 0) AS total_tokens,
                       COALESCE(SUM(total_cost), 0) AS total_cost,
                       COALESCE(MAX(currency), ?) AS currency,
                       COALESCE(AVG(latency_ms), 0) AS avg_latency_ms,
                       COALESCE(AVG(ttft_ms), 0) AS avg_ttft_ms
                FROM ai_model_call_log
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        args.add(hasText(tenantId) ? tenantId : "ALL");
        args.add(DEFAULT_CURRENCY);
        if (hasText(tenantId)) {
            sql.append(" AND tenant_id = ?");
            args.add(tenantId);
        }
        if (from != null) {
            sql.append(" AND created_at >= ?");
            args.add(Timestamp.valueOf(from.atStartOfDay()));
        }
        if (to != null) {
            sql.append(" AND created_at < ?");
            args.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
        }
        List<ModelUsageSummaryResponse> rows = jdbcTemplate.query(sql.toString(), this::mapSummary, args.toArray());
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        return new ModelUsageSummaryResponse(
                tenantId,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP),
                DEFAULT_CURRENCY,
                0,
                0
        );
    }

    private ModelPrice priceFor(String provider, String model) {
        List<ModelPrice> rows = jdbcTemplate.query("""
                SELECT * FROM ai_model_price
                WHERE provider = ? AND model = ? AND enabled = 1
                ORDER BY effective_from DESC, id DESC
                LIMIT 1
                """, this::mapPrice, provider, model);
        return rows.stream().findFirst()
                .orElse(new ModelPrice(DEFAULT_CURRENCY, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private BigDecimal cost(Integer tokens, BigDecimal pricePer1mTokens) {
        if (tokens == null || tokens <= 0 || pricePer1mTokens == null) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return pricePer1mTokens.multiply(BigDecimal.valueOf(tokens))
                .divide(BigDecimal.valueOf(1_000_000L), 8, RoundingMode.HALF_UP);
    }

    private ModelCallLogResponse mapLog(ResultSet rs, int rowNum) throws SQLException {
        return new ModelCallLogResponse(
                rs.getString("trace_id"),
                rs.getString("tenant_id"),
                rs.getString("user_id"),
                rs.getString("conversation_id"),
                rs.getString("scene"),
                rs.getString("route_key"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getBoolean("streaming"),
                intOrNull(rs, "prompt_tokens"),
                intOrNull(rs, "completion_tokens"),
                intOrNull(rs, "total_tokens"),
                rs.getBoolean("usage_estimated"),
                rs.getBigDecimal("input_cost"),
                rs.getBigDecimal("output_cost"),
                rs.getBigDecimal("total_cost"),
                rs.getString("currency"),
                rs.getLong("latency_ms"),
                longOrNull(rs, "ttft_ms"),
                rs.getString("status"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                rs.getString("fallback_from"),
                rs.getString("fallback_reason"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private ModelUsageSummaryResponse mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new ModelUsageSummaryResponse(
                rs.getString("tenant_id"),
                rs.getLong("total_calls"),
                rs.getLong("success_calls"),
                rs.getLong("failed_calls"),
                rs.getLong("streaming_calls"),
                rs.getLong("prompt_tokens"),
                rs.getLong("completion_tokens"),
                rs.getLong("total_tokens"),
                rs.getBigDecimal("total_cost"),
                rs.getString("currency"),
                round(rs.getDouble("avg_latency_ms")),
                round(rs.getDouble("avg_ttft_ms"))
        );
    }

    private ModelPrice mapPrice(ResultSet rs, int rowNum) throws SQLException {
        return new ModelPrice(
                rs.getString("currency"),
                rs.getBigDecimal("input_price_per_1m_tokens"),
                rs.getBigDecimal("output_price_per_1m_tokens")
        );
    }

    private Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record ModelPrice(String currency, BigDecimal inputPricePer1mTokens,
                              BigDecimal outputPricePer1mTokens) {
    }
}
