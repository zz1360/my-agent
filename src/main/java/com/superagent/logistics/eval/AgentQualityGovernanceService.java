package com.superagent.logistics.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.logistics.api.dto.FeedbackTagResponse;
import com.superagent.logistics.api.dto.QualityAlertEvaluationResponse;
import com.superagent.logistics.api.dto.QualityAlertResponse;
import com.superagent.logistics.api.dto.QualityAlertRuleResponse;
import com.superagent.logistics.security.AccessDeniedException;
import com.superagent.logistics.security.AgentPermissionService;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentQualityGovernanceService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AgentPermissionService permissionService;

    public AgentQualityGovernanceService(JdbcTemplate jdbcTemplate,
                                         ObjectMapper objectMapper,
                                         AgentPermissionService permissionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.permissionService = permissionService;
    }

    public List<FeedbackTagResponse> listFeedbackTags(String tenantId, String userId, List<String> roles,
                                                      boolean enabledOnly) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        StringBuilder sql = new StringBuilder("""
                SELECT *
                FROM ai_feedback_tag_dictionary
                WHERE tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(context.tenantId());
        if (enabledOnly) {
            sql.append(" AND enabled = 1");
        }
        sql.append(" ORDER BY sort_order, tag_code");
        return jdbcTemplate.query(sql.toString(), this::mapFeedbackTag, args.toArray());
    }

    public List<QualityAlertRuleResponse> listAlertRules(String tenantId, String userId, List<String> roles,
                                                         boolean enabledOnly) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        checkQualityMaintainer(context);
        StringBuilder sql = new StringBuilder("""
                SELECT *
                FROM ai_quality_alert_rule
                WHERE tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(context.tenantId());
        if (enabledOnly) {
            sql.append(" AND enabled = 1");
        }
        sql.append(" ORDER BY metric_type, rule_id");
        return jdbcTemplate.query(sql.toString(), this::mapAlertRule, args.toArray());
    }

    public List<QualityAlertResponse> listAlerts(String tenantId, String userId, List<String> roles,
                                                 String status, int limit) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        checkQualityMaintainer(context);
        StringBuilder sql = new StringBuilder("""
                SELECT *
                FROM ai_quality_alert
                WHERE tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(context.tenantId());
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY last_triggered_at DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 100)));
        return jdbcTemplate.query(sql.toString(), this::mapAlert, args.toArray());
    }

    public QualityAlertEvaluationResponse evaluateAlerts(String tenantId, String userId, List<String> roles) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        checkQualityMaintainer(context);
        List<QualityAlertRuleResponse> rules = listAlertRules(context.tenantId(), context.userId(), new ArrayList<>(context.roles()), true);
        int resolvedAlerts = 0;
        for (QualityAlertRuleResponse rule : rules) {
            MetricSnapshot metric = metricSnapshot(context.tenantId(), rule);
            boolean triggered = metric.value().compareTo(rule.thresholdValue()) > 0;
            if (triggered) {
                upsertOpenAlert(context.tenantId(), rule, metric);
            } else {
                resolvedAlerts += resolveOpenAlerts(context.tenantId(), rule.ruleId());
            }
        }
        List<QualityAlertResponse> alerts = listAlerts(context.tenantId(), context.userId(), new ArrayList<>(context.roles()), "OPEN", 50);
        return new QualityAlertEvaluationResponse(rules.size(), alerts.size(), resolvedAlerts, alerts);
    }

    private MetricSnapshot metricSnapshot(String tenantId, QualityAlertRuleResponse rule) {
        Instant start = Instant.now().minus(Math.max(1, rule.windowDays()), ChronoUnit.DAYS);
        Timestamp from = Timestamp.from(start);
        return switch (rule.metricType()) {
            case "NEGATIVE_RATE" -> negativeRate(tenantId, from, rule.windowDays());
            case "REVIEW_BACKLOG" -> reviewBacklog(tenantId);
            case "RAG_FAILURE_RATE" -> ragFailureRate(tenantId, from, rule.windowDays());
            default -> new MetricSnapshot(BigDecimal.ZERO, rule.metricType() + " 暂无计算逻辑", "{}");
        };
    }

    private MetricSnapshot negativeRate(String tenantId, Timestamp from, int windowDays) {
        long total = count("""
                SELECT COUNT(*) FROM ai_agent_message_feedback
                WHERE tenant_id = ? AND created_at >= ?
                """, tenantId, from);
        long negative = count("""
                SELECT COUNT(*) FROM ai_agent_message_feedback
                WHERE tenant_id = ? AND rating = 'NOT_HELPFUL' AND created_at >= ?
                """, tenantId, from);
        BigDecimal value = ratio(negative, total);
        return new MetricSnapshot(value, "近 " + windowDays + " 天负反馈率 " + percent(value), toJson(Map.of(
                "totalFeedback", total,
                "notHelpfulFeedback", negative,
                "windowDays", windowDays
        )));
    }

    private MetricSnapshot reviewBacklog(String tenantId) {
        long backlog = count("""
                SELECT COUNT(*) FROM ai_eval_case_candidate
                WHERE tenant_id = ? AND review_status IN ('UNREVIEWED', 'REVIEWING')
                """, tenantId);
        BigDecimal value = BigDecimal.valueOf(backlog).setScale(4, RoundingMode.HALF_UP);
        return new MetricSnapshot(value, "待审批候选积压 " + backlog + " 条", toJson(Map.of(
                "backlogCandidates", backlog,
                "reviewStatus", List.of("UNREVIEWED", "REVIEWING")
        )));
    }

    private MetricSnapshot ragFailureRate(String tenantId, Timestamp from, int windowDays) {
        long total = count("""
                SELECT COUNT(*) FROM ai_rag_experiment_run
                WHERE tenant_id = ? AND created_at >= ?
                """, tenantId, from);
        long failed = count("""
                SELECT COUNT(*) FROM ai_rag_experiment_run
                WHERE tenant_id = ? AND status = 'FAILED' AND created_at >= ?
                """, tenantId, from);
        BigDecimal value = ratio(failed, total);
        return new MetricSnapshot(value, "近 " + windowDays + " 天 RAG 实验失败率 " + percent(value), toJson(Map.of(
                "totalRuns", total,
                "failedRuns", failed,
                "windowDays", windowDays
        )));
    }

    private void upsertOpenAlert(String tenantId, QualityAlertRuleResponse rule, MetricSnapshot metric) {
        List<String> existing = jdbcTemplate.query("""
                        SELECT alert_id
                        FROM ai_quality_alert
                        WHERE tenant_id = ? AND rule_id = ? AND status = 'OPEN'
                        ORDER BY last_triggered_at DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> rs.getString("alert_id"),
                tenantId,
                rule.ruleId());
        Instant now = Instant.now();
        String summary = rule.ruleName() + "：" + metric.summary();
        if (existing.isEmpty()) {
            jdbcTemplate.update("""
                            INSERT INTO ai_quality_alert
                            (tenant_id, alert_id, rule_id, metric_type, severity, status, metric_value,
                             threshold_value, window_days, summary, detail_json, first_triggered_at,
                             last_triggered_at, resolved_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    tenantId,
                    "alert-" + UUID.randomUUID().toString().substring(0, 12),
                    rule.ruleId(),
                    rule.metricType(),
                    rule.severity(),
                    "OPEN",
                    metric.value(),
                    rule.thresholdValue(),
                    rule.windowDays(),
                    summary,
                    metric.detailJson(),
                    Timestamp.from(now),
                    Timestamp.from(now),
                    null);
            return;
        }
        jdbcTemplate.update("""
                        UPDATE ai_quality_alert
                        SET metric_value = ?, threshold_value = ?, summary = ?, detail_json = ?,
                            last_triggered_at = ?, resolved_at = NULL
                        WHERE tenant_id = ? AND alert_id = ?
                        """,
                metric.value(),
                rule.thresholdValue(),
                summary,
                metric.detailJson(),
                Timestamp.from(now),
                tenantId,
                existing.get(0));
    }

    private int resolveOpenAlerts(String tenantId, String ruleId) {
        return jdbcTemplate.update("""
                        UPDATE ai_quality_alert
                        SET status = 'RESOLVED', resolved_at = ?
                        WHERE tenant_id = ? AND rule_id = ? AND status = 'OPEN'
                        """,
                Timestamp.from(Instant.now()),
                tenantId,
                ruleId);
    }

    private FeedbackTagResponse mapFeedbackTag(ResultSet rs, int rowNum) throws SQLException {
        return new FeedbackTagResponse(
                rs.getString("tag_code"),
                rs.getString("tag_name"),
                rs.getString("category"),
                rs.getString("description"),
                rs.getBoolean("enabled"),
                rs.getInt("sort_order"),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private QualityAlertRuleResponse mapAlertRule(ResultSet rs, int rowNum) throws SQLException {
        return new QualityAlertRuleResponse(
                rs.getString("rule_id"),
                rs.getString("rule_name"),
                rs.getString("metric_type"),
                rs.getBigDecimal("threshold_value"),
                rs.getInt("window_days"),
                rs.getString("severity"),
                rs.getBoolean("enabled"),
                rs.getString("description"),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private QualityAlertResponse mapAlert(ResultSet rs, int rowNum) throws SQLException {
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        return new QualityAlertResponse(
                rs.getString("alert_id"),
                rs.getString("rule_id"),
                rs.getString("metric_type"),
                rs.getString("severity"),
                rs.getString("status"),
                rs.getBigDecimal("metric_value"),
                rs.getBigDecimal("threshold_value"),
                rs.getInt("window_days"),
                rs.getString("summary"),
                rs.getString("detail_json"),
                rs.getTimestamp("first_triggered_at").toInstant(),
                rs.getTimestamp("last_triggered_at").toInstant(),
                resolvedAt == null ? null : resolvedAt.toInstant()
        );
    }

    private void checkQualityMaintainer(AgentUserContext context) {
        if (!context.hasAnyRole("ADMIN", "OPS_MANAGER", "OPERATIONS")) {
            throw new AccessDeniedException("当前用户没有质量治理权限");
        }
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private String percent(BigDecimal value) {
        return value.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) + "%";
    }

    private String toJson(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private record MetricSnapshot(BigDecimal value, String summary, String detailJson) {
    }
}
