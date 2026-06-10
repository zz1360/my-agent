package com.superagent.logistics.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.logistics.api.dto.AgentActionAutomationRequest;
import com.superagent.logistics.api.dto.AgentActionAutomationResponse;
import com.superagent.logistics.api.dto.AgentActionBusinessLinkResponse;
import com.superagent.logistics.api.dto.AgentActionExecuteRequest;
import com.superagent.logistics.api.dto.AgentActionExecutionMetricRow;
import com.superagent.logistics.api.dto.AgentActionExecutionMetricsResponse;
import com.superagent.logistics.api.dto.AgentActionExecutionResponse;
import com.superagent.logistics.api.dto.AgentActionResponse;
import com.superagent.logistics.security.AccessDeniedException;
import com.superagent.logistics.security.AgentPermissionService;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AgentActionExecutionService {

    private static final int MAX_RETRY_COUNT = 3;
    private static final Duration RETRY_DELAY = Duration.ofMinutes(5);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AgentActionService actionService;
    private final AgentPermissionService permissionService;
    private final Map<String, AgentActionExecutor> executors;

    public AgentActionExecutionService(JdbcTemplate jdbcTemplate,
                                       ObjectMapper objectMapper,
                                       AgentActionService actionService,
                                       AgentPermissionService permissionService,
                                       List<AgentActionExecutor> executors) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.actionService = actionService;
        this.permissionService = permissionService;
        this.executors = executors.stream()
                .collect(Collectors.toMap(AgentActionExecutor::actionType, Function.identity()));
    }

    public AgentActionExecutionResponse execute(String actionId, AgentActionExecuteRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        checkExecutorPermission(context);
        AgentActionResponse action = actionService.get(context.tenantId(), context.userId(), List.copyOf(context.roles()), actionId);
        AgentActionExecutor executor = findExecutor(action.actionType());
        boolean force = Boolean.TRUE.equals(request.force());
        if (!executor.lowRisk() && !force) {
            throw new IllegalArgumentException("该动作不是低风险自动执行动作，需要 force=true 后由人工确认触发");
        }
        String idempotencyKey = resolveIdempotencyKey(action, executor, request);
        return findSuccessfulByIdempotency(context.tenantId(), idempotencyKey)
                .or(() -> findAlreadyAppliedExecution(context.tenantId(), action))
                .orElseGet(() -> {
                    if (!"APPROVED".equals(action.status())) {
                        throw new IllegalArgumentException("只有 APPROVED 状态的动作才能执行");
                    }
                    return executeWithExecutor(context, action, executor, request, force, idempotencyKey, 0);
                });
    }

    public AgentActionAutomationResponse runLowRiskAutomation(AgentActionAutomationRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        checkExecutorPermission(context);
        if (request.customerId() == null || request.customerId().isBlank()) {
            permissionService.checkBusinessReadable(context);
        } else {
            permissionService.checkCustomerReadable(context, request.customerId());
        }

        int limit = request.limit() == null ? 20 : Math.max(1, Math.min(request.limit(), 100));
        List<AgentActionResponse> approvedActions = actionService.list(
                context.tenantId(), context.userId(), List.copyOf(context.roles()),
                request.customerId(), "APPROVED", limit);
        List<AgentActionExecutionResponse> executions = new ArrayList<>();
        int skipped = 0;
        for (AgentActionResponse action : approvedActions) {
            AgentActionExecutor executor = findExecutor(action.actionType());
            if (!executor.lowRisk()) {
                skipped++;
                continue;
            }
            AgentActionExecuteRequest executeRequest = new AgentActionExecuteRequest(
                    context.tenantId(), context.userId(), List.copyOf(context.roles()), false,
                    "低风险动作自动执行", "auto-" + action.actionId(), false);
            executions.add(executeWithExecutor(context, action, executor, executeRequest, false,
                    resolveIdempotencyKey(action, executor, executeRequest), 0));
        }
        return new AgentActionAutomationResponse(context.tenantId(), request.customerId(),
                approvedActions.size(), executions.size(), skipped, executions);
    }

    public AgentActionExecutionResponse retry(String executionId, AgentActionExecuteRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        checkExecutorPermission(context);
        AgentActionExecutionResponse failed = findExecution(context.tenantId(), executionId);
        if (!"FAILED".equals(failed.status())) {
            throw new IllegalArgumentException("只有 FAILED 执行记录可以重试");
        }
        if (failed.retryCount() >= failed.maxRetryCount()) {
            throw new IllegalArgumentException("执行记录已达到最大重试次数");
        }
        AgentActionResponse action = actionService.get(context.tenantId(), context.userId(),
                List.copyOf(context.roles()), failed.actionId());
        AgentActionExecutor executor = findExecutor(action.actionType());
        boolean force = Boolean.TRUE.equals(request.force());
        if (!executor.lowRisk() && !force) {
            throw new IllegalArgumentException("该动作不是低风险自动执行动作，需要 force=true 后由人工确认触发");
        }
        String idempotencyKey = failed.idempotencyKey() == null || failed.idempotencyKey().isBlank()
                ? resolveIdempotencyKey(action, executor, request)
                : failed.idempotencyKey();
        return findSuccessfulByIdempotency(context.tenantId(), idempotencyKey)
                .orElseGet(() -> {
                    if (!"APPROVED".equals(action.status())) {
                        throw new IllegalArgumentException("只有 APPROVED 状态的动作才能重试执行");
                    }
                    AgentActionExecuteRequest retryRequest = new AgentActionExecuteRequest(
                            context.tenantId(), context.userId(), List.copyOf(context.roles()), force,
                            request.comment(), idempotencyKey, request.simulateFailure());
                    return executeWithExecutor(context, action, executor, retryRequest, force,
                            idempotencyKey, failed.retryCount() + 1);
                });
    }

    public List<AgentActionExecutionResponse> listExecutions(String actionId,
                                                             String tenantId,
                                                             String userId,
                                                             List<String> roles) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        actionService.get(context.tenantId(), context.userId(), List.copyOf(context.roles()), actionId);
        return jdbcTemplate.query("""
                SELECT * FROM ai_agent_action_execution
                WHERE tenant_id = ? AND action_id = ?
                ORDER BY started_at DESC
                """, this::mapExecution, context.tenantId(), actionId);
    }

    public List<AgentActionExecutionResponse> searchExecutions(String tenantId,
                                                               String userId,
                                                               List<String> roles,
                                                               String status,
                                                               String actionType,
                                                               String executorName,
                                                               String targetSystem,
                                                               LocalDate from,
                                                               LocalDate to,
                                                               int limit) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        checkExecutorPermission(context);
        return queryExecutions(context.tenantId(), status, actionType, executorName, targetSystem,
                from, to, Math.max(1, Math.min(limit, 200)));
    }

    public List<AgentActionExecutionResponse> retryQueue(String tenantId,
                                                         String userId,
                                                         List<String> roles,
                                                         boolean dueOnly,
                                                         int limit) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        checkExecutorPermission(context);
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM ai_agent_action_execution
                WHERE tenant_id = ? AND status = 'FAILED' AND next_retry_at IS NOT NULL
                """);
        List<Object> args = new ArrayList<>();
        args.add(context.tenantId());
        if (dueOnly) {
            sql.append(" AND next_retry_at <= ?");
            args.add(Timestamp.from(Instant.now()));
        }
        sql.append(" ORDER BY next_retry_at ASC, started_at ASC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 200)));
        return jdbcTemplate.query(sql.toString(), this::mapExecution, args.toArray());
    }

    public AgentActionExecutionMetricsResponse metrics(String tenantId,
                                                       String userId,
                                                       List<String> roles,
                                                       LocalDate from,
                                                       LocalDate to) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        checkExecutorPermission(context);
        List<AgentActionExecutionResponse> rows = queryExecutions(context.tenantId(), null, null, null,
                null, from, to, 5000);
        long total = rows.size();
        long success = rows.stream().filter(row -> "SUCCESS".equals(row.status())).count();
        long failed = rows.stream().filter(row -> "FAILED".equals(row.status())).count();
        long retryableFailed = rows.stream()
                .filter(row -> "FAILED".equals(row.status()) && row.nextRetryAt() != null
                        && row.retryCount() < row.maxRetryCount())
                .count();
        return new AgentActionExecutionMetricsResponse(
                context.tenantId(),
                from,
                to,
                total,
                success,
                failed,
                retryableFailed,
                rate(success, total),
                rate(failed, total),
                metricRows(rows, AgentActionExecutionResponse::actionType),
                metricRows(rows, AgentActionExecutionResponse::executorName),
                metricRows(rows, AgentActionExecutionResponse::targetSystem)
        );
    }

    public AgentActionBusinessLinkResponse businessLink(String actionId,
                                                        String tenantId,
                                                        String userId,
                                                        List<String> roles) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        checkExecutorPermission(context);
        AgentActionResponse action = actionService.get(context.tenantId(), context.userId(), List.copyOf(context.roles()), actionId);
        Optional<AgentActionExecutionResponse> latestExecution = latestSuccessfulExecution(context.tenantId(), action.actionId());
        return switch (action.actionType()) {
            case "TICKET_NOTE" -> ticketNoteLink(action, latestExecution);
            case "OPERATIONS_FOLLOW_UP" -> opsTaskLink(action, latestExecution);
            case "CUSTOMER_REPLY" -> replyDraftLink(action, latestExecution);
            case "COMPENSATION_REVIEW" -> compensationReviewLink(action, latestExecution);
            default -> new AgentActionBusinessLinkResponse(
                    action.tenantId(), action.actionId(), action.actionType(), null, null,
                    action.customerId(), action.waybillId(), action.status(), null,
                    action.traceId(), latestExecution.map(AgentActionExecutionResponse::executionId).orElse(null));
        };
    }

    private AgentActionExecutionResponse executeWithExecutor(AgentUserContext context,
                                                             AgentActionResponse action,
                                                             AgentActionExecutor executor,
                                                             AgentActionExecuteRequest request,
                                                             boolean forced,
                                                             String idempotencyKey,
                                                             int retryCount) {
        String executionId = "exec-" + UUID.randomUUID().toString().substring(0, 12);
        Instant startedAt = Instant.now();
        String requestJson = requestJson(action, request, forced, idempotencyKey, retryCount);
        try {
            if (Boolean.TRUE.equals(request.simulateFailure())) {
                throw new IllegalArgumentException("模拟外部系统执行失败");
            }
            ActionExecutionResult result = executor.execute(action, context, request);
            String responseJson = responseJson(result);
            Instant finishedAt = Instant.now();
            insertExecution(context, action, executor, executionId, "SUCCESS", requestJson,
                    responseJson, null, result.externalRefId(), idempotencyKey, retryCount,
                    null, startedAt, finishedAt);
            markActionApplied(context.tenantId(), action.actionId(), finishedAt);
            return findExecution(context.tenantId(), executionId);
        } catch (RuntimeException ex) {
            Instant finishedAt = Instant.now();
            Instant nextRetryAt = retryCount >= MAX_RETRY_COUNT ? null : finishedAt.plus(RETRY_DELAY);
            insertExecution(context, action, executor, executionId, "FAILED", requestJson,
                    null, ex.getMessage(), null, idempotencyKey, retryCount,
                    nextRetryAt, startedAt, finishedAt);
            throw ex;
        }
    }

    private List<AgentActionExecutionResponse> queryExecutions(String tenantId,
                                                               String status,
                                                               String actionType,
                                                               String executorName,
                                                               String targetSystem,
                                                               LocalDate from,
                                                               LocalDate to,
                                                               int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM ai_agent_action_execution
                WHERE tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.trim().toUpperCase());
        }
        if (actionType != null && !actionType.isBlank()) {
            sql.append(" AND action_type = ?");
            args.add(actionType.trim().toUpperCase());
        }
        if (executorName != null && !executorName.isBlank()) {
            sql.append(" AND executor_name = ?");
            args.add(executorName.trim());
        }
        if (targetSystem != null && !targetSystem.isBlank()) {
            sql.append(" AND target_system = ?");
            args.add(targetSystem.trim());
        }
        if (from != null) {
            sql.append(" AND started_at >= ?");
            args.add(Timestamp.valueOf(from.atStartOfDay()));
        }
        if (to != null) {
            sql.append(" AND started_at < ?");
            args.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
        }
        sql.append(" ORDER BY started_at DESC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), this::mapExecution, args.toArray());
    }

    private void insertExecution(AgentUserContext context,
                                 AgentActionResponse action,
                                 AgentActionExecutor executor,
                                 String executionId,
                                 String status,
                                 String requestJson,
                                 String responseJson,
                                 String failureReason,
                                 String externalRefId,
                                 String idempotencyKey,
                                 int retryCount,
                                 Instant nextRetryAt,
                                 Instant startedAt,
                                 Instant finishedAt) {
        jdbcTemplate.update("""
                INSERT INTO ai_agent_action_execution
                (tenant_id, execution_id, action_id, action_type, executor_name, target_system,
                 low_risk, status, request_json, response_json, failure_reason, retry_count,
                 executed_by, started_at, finished_at, external_ref_id, idempotency_key, next_retry_at, max_retry_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, context.tenantId(), executionId, action.actionId(), action.actionType(),
                executor.executorName(), executor.targetSystem(), executor.lowRisk(),
                status, requestJson, responseJson, failureReason, retryCount, context.userId(),
                Timestamp.from(startedAt), Timestamp.from(finishedAt), externalRefId, idempotencyKey,
                nextRetryAt == null ? null : Timestamp.from(nextRetryAt), MAX_RETRY_COUNT);
    }

    private void markActionApplied(String tenantId, String actionId, Instant now) {
        jdbcTemplate.update("""
                UPDATE ai_agent_action_draft
                SET status = ?, updated_at = ?
                WHERE tenant_id = ? AND action_id = ?
                """, "APPLIED", Timestamp.from(now), tenantId, actionId);
    }

    private AgentActionExecutionResponse findExecution(String tenantId, String executionId) {
        return jdbcTemplate.query("""
                SELECT * FROM ai_agent_action_execution
                WHERE tenant_id = ? AND execution_id = ?
                LIMIT 1
                """, this::mapExecution, tenantId, executionId).stream()
                .findFirst()
                .orElseThrow();
    }

    private Optional<AgentActionExecutionResponse> latestSuccessfulExecution(String tenantId, String actionId) {
        return jdbcTemplate.query("""
                SELECT * FROM ai_agent_action_execution
                WHERE tenant_id = ? AND action_id = ? AND status = 'SUCCESS'
                ORDER BY started_at DESC
                LIMIT 1
                """, this::mapExecution, tenantId, actionId).stream().findFirst();
    }

    private AgentActionExecutor findExecutor(String actionType) {
        AgentActionExecutor executor = executors.get(actionType);
        if (executor == null) {
            throw new IllegalArgumentException("暂不支持执行动作类型 " + actionType);
        }
        return executor;
    }

    private String resolveIdempotencyKey(AgentActionResponse action,
                                         AgentActionExecutor executor,
                                         AgentActionExecuteRequest request) {
        String requestKey = request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                ? "default"
                : request.idempotencyKey().trim();
        return action.actionId() + ":" + executor.executorName() + ":" + requestKey;
    }

    private Optional<AgentActionExecutionResponse> findSuccessfulByIdempotency(String tenantId, String idempotencyKey) {
        return jdbcTemplate.query("""
                SELECT * FROM ai_agent_action_execution
                WHERE tenant_id = ? AND idempotency_key = ? AND status = 'SUCCESS'
                ORDER BY started_at DESC
                LIMIT 1
                """, this::mapExecution, tenantId, idempotencyKey).stream().findFirst();
    }

    private Optional<AgentActionExecutionResponse> findAlreadyAppliedExecution(String tenantId, AgentActionResponse action) {
        if (!"APPLIED".equals(action.status())) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT * FROM ai_agent_action_execution
                WHERE tenant_id = ? AND action_id = ? AND status = 'SUCCESS'
                ORDER BY started_at DESC
                LIMIT 1
                """, this::mapExecution, tenantId, action.actionId()).stream().findFirst();
    }

    private List<AgentActionExecutionMetricRow> metricRows(List<AgentActionExecutionResponse> rows,
                                                           Function<AgentActionExecutionResponse, String> classifier) {
        return rows.stream()
                .collect(Collectors.groupingBy(row -> {
                    String name = classifier.apply(row);
                    return name == null || name.isBlank() ? "UNKNOWN" : name;
                }))
                .entrySet().stream()
                .map(entry -> {
                    long total = entry.getValue().size();
                    long success = entry.getValue().stream().filter(row -> "SUCCESS".equals(row.status())).count();
                    long failed = entry.getValue().stream().filter(row -> "FAILED".equals(row.status())).count();
                    return new AgentActionExecutionMetricRow(entry.getKey(), total, success, failed, rate(success, total));
                })
                .sorted(Comparator.comparingLong(AgentActionExecutionMetricRow::total).reversed()
                        .thenComparing(AgentActionExecutionMetricRow::name))
                .toList();
    }

    private double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return 0;
        }
        return Math.round(numerator * 1000.0 / denominator) / 10.0;
    }

    private AgentActionBusinessLinkResponse ticketNoteLink(AgentActionResponse action,
                                                           Optional<AgentActionExecutionResponse> latestExecution) {
        return jdbcTemplate.query("""
                SELECT note_id AS business_id, customer_id, waybill_id, status, created_at
                FROM logistics_ticket_note
                WHERE tenant_id = ? AND action_id = ?
                LIMIT 1
                """, rs -> rs.next() ? businessLink(action, "logistics_ticket_note", rs, latestExecution) : emptyBusinessLink(action, latestExecution),
                action.tenantId(), action.actionId());
    }

    private AgentActionBusinessLinkResponse opsTaskLink(AgentActionResponse action,
                                                        Optional<AgentActionExecutionResponse> latestExecution) {
        return jdbcTemplate.query("""
                SELECT task_id AS business_id, customer_id, NULL AS waybill_id, status, created_at
                FROM logistics_ops_task
                WHERE tenant_id = ? AND action_id = ?
                LIMIT 1
                """, rs -> rs.next() ? businessLink(action, "logistics_ops_task", rs, latestExecution) : emptyBusinessLink(action, latestExecution),
                action.tenantId(), action.actionId());
    }

    private AgentActionBusinessLinkResponse replyDraftLink(AgentActionResponse action,
                                                           Optional<AgentActionExecutionResponse> latestExecution) {
        return jdbcTemplate.query("""
                SELECT reply_id AS business_id, customer_id, NULL AS waybill_id, status, created_at
                FROM logistics_customer_reply_draft
                WHERE tenant_id = ? AND action_id = ?
                LIMIT 1
                """, rs -> rs.next() ? businessLink(action, "logistics_customer_reply_draft", rs, latestExecution) : emptyBusinessLink(action, latestExecution),
                action.tenantId(), action.actionId());
    }

    private AgentActionBusinessLinkResponse compensationReviewLink(AgentActionResponse action,
                                                                   Optional<AgentActionExecutionResponse> latestExecution) {
        return jdbcTemplate.query("""
                SELECT review_id AS business_id, customer_id, waybill_id, status, created_at
                FROM logistics_compensation_review_task
                WHERE tenant_id = ? AND action_id = ?
                LIMIT 1
                """, rs -> rs.next() ? businessLink(action, "logistics_compensation_review_task", rs, latestExecution) : emptyBusinessLink(action, latestExecution),
                action.tenantId(), action.actionId());
    }

    private AgentActionBusinessLinkResponse businessLink(AgentActionResponse action,
                                                         String tableName,
                                                         ResultSet rs,
                                                         Optional<AgentActionExecutionResponse> latestExecution) throws SQLException {
        return new AgentActionBusinessLinkResponse(
                action.tenantId(),
                action.actionId(),
                action.actionType(),
                tableName,
                rs.getString("business_id"),
                rs.getString("customer_id"),
                rs.getString("waybill_id"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at")),
                action.traceId(),
                latestExecution.map(AgentActionExecutionResponse::executionId).orElse(null)
        );
    }

    private AgentActionBusinessLinkResponse emptyBusinessLink(AgentActionResponse action,
                                                              Optional<AgentActionExecutionResponse> latestExecution) {
        return new AgentActionBusinessLinkResponse(
                action.tenantId(),
                action.actionId(),
                action.actionType(),
                null,
                null,
                action.customerId(),
                action.waybillId(),
                action.status(),
                null,
                action.traceId(),
                latestExecution.map(AgentActionExecutionResponse::executionId).orElse(null)
        );
    }

    private void checkExecutorPermission(AgentUserContext context) {
        if (!context.hasAnyRole("OPERATIONS", "OPS_MANAGER", "ADMIN")) {
            throw new AccessDeniedException("当前用户没有动作执行权限");
        }
    }

    private String requestJson(AgentActionResponse action,
                               AgentActionExecuteRequest request,
                               boolean forced,
                               String idempotencyKey,
                               int retryCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actionId", action.actionId());
        payload.put("actionType", action.actionType());
        payload.put("force", forced);
        payload.put("comment", request.comment());
        payload.put("idempotencyKey", idempotencyKey);
        payload.put("retryCount", retryCount);
        payload.put("simulateFailure", Boolean.TRUE.equals(request.simulateFailure()));
        return toJson(payload);
    }

    private String responseJson(ActionExecutionResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetSystem", result.targetSystem());
        payload.put("externalRefId", result.externalRefId());
        payload.put("response", result.response());
        return toJson(payload);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("动作执行日志序列化失败", ex);
        }
    }

    private AgentActionExecutionResponse mapExecution(ResultSet rs, int rowNum) throws SQLException {
        return new AgentActionExecutionResponse(
                rs.getString("tenant_id"),
                rs.getString("execution_id"),
                rs.getString("action_id"),
                rs.getString("action_type"),
                rs.getString("executor_name"),
                rs.getString("target_system"),
                rs.getString("external_ref_id"),
                rs.getString("idempotency_key"),
                rs.getBoolean("low_risk"),
                rs.getString("status"),
                rs.getString("request_json"),
                rs.getString("response_json"),
                rs.getString("failure_reason"),
                rs.getInt("retry_count"),
                rs.getInt("max_retry_count"),
                toInstant(rs.getTimestamp("next_retry_at")),
                rs.getString("executed_by"),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("finished_at"))
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
