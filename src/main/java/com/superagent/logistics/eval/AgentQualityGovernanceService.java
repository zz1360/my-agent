package com.superagent.logistics.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.logistics.api.dto.FeedbackTagUpsertRequest;
import com.superagent.logistics.api.dto.FeedbackTagResponse;
import com.superagent.logistics.api.dto.QualityAlertEvaluationResponse;
import com.superagent.logistics.api.dto.QualityAlertResponse;
import com.superagent.logistics.api.dto.QualityAlertRuleUpsertRequest;
import com.superagent.logistics.api.dto.QualityAlertRuleResponse;
import com.superagent.logistics.api.dto.QualityAlertTaskDetailResponse;
import com.superagent.logistics.api.dto.QualityAlertTaskResponse;
import com.superagent.logistics.api.dto.QualityAlertTaskUpdateRequest;
import com.superagent.logistics.api.dto.QualityTrendResponse;
import com.superagent.logistics.api.dto.QualityTrendResponse.DailyQualityPoint;
import com.superagent.logistics.api.dto.QualityTrendResponse.MetricCount;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AgentQualityGovernanceService {

    private static final Set<String> SUPPORTED_METRICS = Set.of("NEGATIVE_RATE", "REVIEW_BACKLOG", "RAG_FAILURE_RATE");
    private static final Set<String> SUPPORTED_SEVERITIES = Set.of("INFO", "WARN", "ERROR");
    private static final Set<String> SUPPORTED_TASK_STATUSES = Set.of("OPEN", "PROCESSING", "RESOLVED", "REJECTED");
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

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

    public FeedbackTagResponse upsertFeedbackTag(String tagCode, FeedbackTagUpsertRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        permissionService.checkBusinessReadable(context);
        checkQualityMaintainer(context);
        String normalizedTagCode = normalizeTagCode(tagCode);
        String tagName = required(request.tagName(), "标签名称不能为空");
        String category = required(request.category(), "标签分类不能为空").toUpperCase(Locale.ROOT);
        boolean enabled = request.enabled() == null || request.enabled();
        int sortOrder = request.sortOrder() == null ? 100 : Math.max(0, request.sortOrder());
        Instant now = Instant.now();
        long existing = count("""
                SELECT COUNT(*) FROM ai_feedback_tag_dictionary
                WHERE tenant_id = ? AND tag_code = ?
                """, context.tenantId(), normalizedTagCode);
        if (existing > 0) {
            jdbcTemplate.update("""
                            UPDATE ai_feedback_tag_dictionary
                            SET tag_name = ?, category = ?, description = ?, enabled = ?, sort_order = ?, updated_at = ?
                            WHERE tenant_id = ? AND tag_code = ?
                            """,
                    tagName,
                    category,
                    blankToNull(request.description()),
                    enabled,
                    sortOrder,
                    Timestamp.from(now),
                    context.tenantId(),
                    normalizedTagCode);
        } else {
            jdbcTemplate.update("""
                            INSERT INTO ai_feedback_tag_dictionary
                            (tenant_id, tag_code, tag_name, category, description, enabled, sort_order, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    context.tenantId(),
                    normalizedTagCode,
                    tagName,
                    category,
                    blankToNull(request.description()),
                    enabled,
                    sortOrder,
                    Timestamp.from(now),
                    Timestamp.from(now));
        }
        return findFeedbackTag(context.tenantId(), normalizedTagCode);
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

    public QualityAlertRuleResponse upsertAlertRule(String ruleId, QualityAlertRuleUpsertRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        permissionService.checkBusinessReadable(context);
        checkQualityMaintainer(context);
        String normalizedRuleId = normalizeRuleId(ruleId);
        String ruleName = required(request.ruleName(), "规则名称不能为空");
        String metricType = required(request.metricType(), "指标类型不能为空").toUpperCase(Locale.ROOT);
        if (!SUPPORTED_METRICS.contains(metricType)) {
            throw new IllegalArgumentException("暂不支持的指标类型：" + metricType);
        }
        BigDecimal thresholdValue = request.thresholdValue();
        if (thresholdValue == null || thresholdValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("阈值必须大于等于 0");
        }
        int windowDays = request.windowDays() == null ? 7 : Math.max(1, Math.min(request.windowDays(), 90));
        String severity = request.severity() == null || request.severity().isBlank()
                ? "WARN"
                : request.severity().trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_SEVERITIES.contains(severity)) {
            throw new IllegalArgumentException("暂不支持的告警级别：" + severity);
        }
        boolean enabled = request.enabled() == null || request.enabled();
        Instant now = Instant.now();
        long existing = count("""
                SELECT COUNT(*) FROM ai_quality_alert_rule
                WHERE tenant_id = ? AND rule_id = ?
                """, context.tenantId(), normalizedRuleId);
        if (existing > 0) {
            jdbcTemplate.update("""
                            UPDATE ai_quality_alert_rule
                            SET rule_name = ?, metric_type = ?, threshold_value = ?, window_days = ?,
                                severity = ?, enabled = ?, description = ?, updated_at = ?
                            WHERE tenant_id = ? AND rule_id = ?
                            """,
                    ruleName,
                    metricType,
                    thresholdValue,
                    windowDays,
                    severity,
                    enabled,
                    blankToNull(request.description()),
                    Timestamp.from(now),
                    context.tenantId(),
                    normalizedRuleId);
        } else {
            jdbcTemplate.update("""
                            INSERT INTO ai_quality_alert_rule
                            (tenant_id, rule_id, rule_name, metric_type, threshold_value, window_days, severity,
                             enabled, description, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    context.tenantId(),
                    normalizedRuleId,
                    ruleName,
                    metricType,
                    thresholdValue,
                    windowDays,
                    severity,
                    enabled,
                    blankToNull(request.description()),
                    Timestamp.from(now),
                    Timestamp.from(now));
        }
        return findAlertRule(context.tenantId(), normalizedRuleId);
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

    public QualityAlertTaskResponse createAlertTask(String alertId, String tenantId, String userId, List<String> roles) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        checkQualityMaintainer(context);
        QualityAlertResponse alert = findAlert(context.tenantId(), alertId);
        String actionId = truncate("quality-alert-" + alert.alertId(), 128);
        List<QualityAlertTaskResponse> existingTasks = jdbcTemplate.query("""
                        SELECT task_id, status, created_at
                        FROM logistics_ops_task
                        WHERE tenant_id = ? AND action_id = ?
                        LIMIT 1
                        """,
                (rs, rowNum) -> new QualityAlertTaskResponse(
                        alert.alertId(),
                        rs.getString("task_id"),
                        actionId,
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                context.tenantId(),
                actionId);
        if (!existingTasks.isEmpty()) {
            QualityAlertTaskResponse task = existingTasks.get(0);
            jdbcTemplate.update("""
                            UPDATE ai_quality_alert
                            SET task_id = ?, task_created_at = ?
                            WHERE tenant_id = ? AND alert_id = ?
                            """,
                    task.taskId(),
                    Timestamp.from(task.createdAt()),
                    context.tenantId(),
                    alert.alertId());
            return task;
        }

        Instant now = Instant.now();
        String taskId = "task-alert-" + UUID.randomUUID().toString().substring(0, 10);
        String description = """
                质量告警需要复核。
                告警ID：%s
                规则ID：%s
                指标：%s，当前值 %s，阈值 %s
                摘要：%s
                详情：%s
                """.formatted(alert.alertId(), alert.ruleId(), alert.metricType(), alert.metricValue(),
                alert.thresholdValue(), alert.summary(), alert.detailJson());
        jdbcTemplate.update("""
                        INSERT INTO logistics_ops_task
                        (tenant_id, task_id, action_id, customer_id, title, description,
                         owner_role, status, created_by, created_at, completed_at, owner_user_id,
                         last_comment, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                context.tenantId(),
                taskId,
                actionId,
                "QUALITY",
                truncate("质量告警复核：" + alert.summary(), 255),
                description,
                "OPS_MANAGER",
                "OPEN",
                context.userId(),
                Timestamp.from(now),
                null,
                null,
                "由质量告警生成，等待运营复核。",
                Timestamp.from(now));
        jdbcTemplate.update("""
                        UPDATE ai_quality_alert
                        SET task_id = ?, task_created_at = ?
                        WHERE tenant_id = ? AND alert_id = ?
                        """,
                taskId,
                Timestamp.from(now),
                context.tenantId(),
                alert.alertId());
        return new QualityAlertTaskResponse(alert.alertId(), taskId, actionId, "OPEN", now);
    }

    public List<QualityAlertTaskDetailResponse> listAlertTasks(String tenantId, String userId, List<String> roles,
                                                               String status, int limit) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        checkQualityMaintainer(context);
        StringBuilder sql = new StringBuilder("""
                SELECT *
                FROM logistics_ops_task
                WHERE tenant_id = ? AND action_id LIKE 'quality-alert-%'
                """);
        List<Object> args = new ArrayList<>();
        args.add(context.tenantId());
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(normalizeTaskStatus(status));
        }
        sql.append(" ORDER BY COALESCE(updated_at, created_at) DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 100)));
        return jdbcTemplate.query(sql.toString(), this::mapAlertTask, args.toArray());
    }

    public QualityAlertTaskDetailResponse transitionAlertTask(String taskId, QualityAlertTaskUpdateRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        permissionService.checkBusinessReadable(context);
        checkQualityMaintainer(context);
        QualityAlertTaskDetailResponse current = findAlertTask(context.tenantId(), taskId);
        String nextStatus = normalizeTaskStatus(request.status());
        String ownerUserId = firstNotBlank(request.ownerUserId(), current.ownerUserId());
        String comment = firstNotBlank(request.comment(), current.lastComment());
        Instant now = Instant.now();
        Timestamp completedAt = ("RESOLVED".equals(nextStatus) || "REJECTED".equals(nextStatus))
                ? Timestamp.from(now)
                : null;
        jdbcTemplate.update("""
                        UPDATE logistics_ops_task
                        SET status = ?, owner_user_id = ?, last_comment = ?, updated_at = ?, completed_at = ?
                        WHERE tenant_id = ? AND task_id = ?
                        """,
                nextStatus,
                ownerUserId,
                comment,
                Timestamp.from(now),
                completedAt,
                context.tenantId(),
                current.taskId());
        if ("RESOLVED".equals(nextStatus) || "REJECTED".equals(nextStatus)) {
            jdbcTemplate.update("""
                            UPDATE ai_quality_alert
                            SET status = 'RESOLVED', resolved_at = ?
                            WHERE tenant_id = ? AND alert_id = ?
                            """,
                    Timestamp.from(now),
                    context.tenantId(),
                    current.alertId());
        }
        return findAlertTask(context.tenantId(), current.taskId());
    }

    public QualityTrendResponse qualityTrends(String tenantId, String userId, List<String> roles,
                                              LocalDate fromDate, LocalDate toDate) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        checkQualityMaintainer(context);
        DateRange range = resolveDateRange(fromDate, toDate);
        List<DailyQualityPoint> points = new ArrayList<>();
        LocalDate cursor = range.fromDate();
        while (!cursor.isAfter(range.toDate())) {
            Instant start = cursor.atStartOfDay(DEFAULT_ZONE).toInstant();
            Instant end = cursor.plusDays(1).atStartOfDay(DEFAULT_ZONE).toInstant();
            Timestamp startTs = Timestamp.from(start);
            Timestamp endTs = Timestamp.from(end);
            points.add(new DailyQualityPoint(
                    cursor,
                    count("""
                            SELECT COUNT(*) FROM ai_quality_alert
                            WHERE tenant_id = ? AND first_triggered_at >= ? AND first_triggered_at < ?
                            """, context.tenantId(), startTs, endTs),
                    count("""
                            SELECT COUNT(*) FROM ai_quality_alert
                            WHERE tenant_id = ? AND resolved_at >= ? AND resolved_at < ?
                            """, context.tenantId(), startTs, endTs),
                    count("""
                            SELECT COUNT(*) FROM logistics_ops_task
                            WHERE tenant_id = ? AND action_id LIKE 'quality-alert-%'
                              AND created_at >= ? AND created_at < ?
                            """, context.tenantId(), startTs, endTs),
                    count("""
                            SELECT COUNT(*) FROM logistics_ops_task
                            WHERE tenant_id = ? AND action_id LIKE 'quality-alert-%'
                              AND completed_at >= ? AND completed_at < ?
                            """, context.tenantId(), startTs, endTs)
            ));
            cursor = cursor.plusDays(1);
        }
        return new QualityTrendResponse(
                range.fromDate(),
                range.toDate(),
                points,
                metricCounts("""
                        SELECT status AS name, COUNT(*) AS metric_count
                        FROM ai_quality_alert
                        WHERE tenant_id = ?
                        GROUP BY status
                        ORDER BY metric_count DESC
                        """, context.tenantId()),
                metricCounts("""
                        SELECT status AS name, COUNT(*) AS metric_count
                        FROM logistics_ops_task
                        WHERE tenant_id = ? AND action_id LIKE 'quality-alert-%'
                        GROUP BY status
                        ORDER BY metric_count DESC
                        """, context.tenantId())
        );
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

    private FeedbackTagResponse findFeedbackTag(String tenantId, String tagCode) {
        return jdbcTemplate.query("""
                        SELECT *
                        FROM ai_feedback_tag_dictionary
                        WHERE tenant_id = ? AND tag_code = ?
                        """,
                this::mapFeedbackTag,
                tenantId,
                tagCode).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到反馈标签：" + tagCode));
    }

    private QualityAlertRuleResponse findAlertRule(String tenantId, String ruleId) {
        return jdbcTemplate.query("""
                        SELECT *
                        FROM ai_quality_alert_rule
                        WHERE tenant_id = ? AND rule_id = ?
                        """,
                this::mapAlertRule,
                tenantId,
                ruleId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到告警规则：" + ruleId));
    }

    private QualityAlertResponse findAlert(String tenantId, String alertId) {
        return jdbcTemplate.query("""
                        SELECT *
                        FROM ai_quality_alert
                        WHERE tenant_id = ? AND alert_id = ?
                        """,
                this::mapAlert,
                tenantId,
                alertId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到质量告警：" + alertId));
    }

    private QualityAlertTaskDetailResponse findAlertTask(String tenantId, String taskId) {
        return jdbcTemplate.query("""
                        SELECT *
                        FROM logistics_ops_task
                        WHERE tenant_id = ? AND task_id = ? AND action_id LIKE 'quality-alert-%'
                        """,
                this::mapAlertTask,
                tenantId,
                taskId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到质量告警任务：" + taskId));
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
        Timestamp taskCreatedAt = rs.getTimestamp("task_created_at");
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
                resolvedAt == null ? null : resolvedAt.toInstant(),
                rs.getString("task_id"),
                taskCreatedAt == null ? null : taskCreatedAt.toInstant()
        );
    }

    private QualityAlertTaskDetailResponse mapAlertTask(ResultSet rs, int rowNum) throws SQLException {
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        Timestamp completedAt = rs.getTimestamp("completed_at");
        return new QualityAlertTaskDetailResponse(
                alertIdFromActionId(rs.getString("action_id")),
                rs.getString("task_id"),
                rs.getString("action_id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("owner_role"),
                rs.getString("owner_user_id"),
                rs.getString("status"),
                rs.getString("last_comment"),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant(),
                updatedAt == null ? rs.getTimestamp("created_at").toInstant() : updatedAt.toInstant(),
                completedAt == null ? null : completedAt.toInstant()
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

    private List<MetricCount> metricCounts(String sql, Object... args) {
        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new MetricCount(rs.getString("name"), rs.getLong("metric_count")), args);
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

    private String normalizeTagCode(String tagCode) {
        String value = required(tagCode, "标签编码不能为空").toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9_-]{2,64}")) {
            throw new IllegalArgumentException("标签编码只能包含字母、数字、下划线或中划线，长度 2-64");
        }
        return value;
    }

    private String normalizeRuleId(String ruleId) {
        String value = required(ruleId, "规则 ID 不能为空");
        if (!value.matches("[A-Za-z0-9_-]{3,128}")) {
            throw new IllegalArgumentException("规则 ID 只能包含字母、数字、下划线或中划线，长度 3-128");
        }
        return value;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNotBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return blankToNull(fallback);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String normalizeTaskStatus(String status) {
        String value = required(status, "任务状态不能为空").toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TASK_STATUSES.contains(value)) {
            throw new IllegalArgumentException("暂不支持的任务状态：" + value);
        }
        return value;
    }

    private String alertIdFromActionId(String actionId) {
        if (actionId == null || !actionId.startsWith("quality-alert-")) {
            return "";
        }
        return actionId.substring("quality-alert-".length());
    }

    private DateRange resolveDateRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate end = toDate == null ? LocalDate.now(DEFAULT_ZONE) : toDate;
        LocalDate start = fromDate == null ? end.minusDays(13) : fromDate;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        if (start.plusDays(60).isBefore(end)) {
            throw new IllegalArgumentException("趋势查询最多支持 61 天");
        }
        return new DateRange(start, end);
    }

    private record MetricSnapshot(BigDecimal value, String summary, String detailJson) {
    }

    private record DateRange(LocalDate fromDate, LocalDate toDate) {
    }
}
