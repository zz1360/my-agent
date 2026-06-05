package com.superagent.logistics.audit;

import com.superagent.logistics.api.dto.AgentChatRequest;
import com.superagent.logistics.api.dto.AuditResponse;
import com.superagent.logistics.api.dto.ToolCallSummary;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AgentAuditService {

    private static final String MODEL_NAME = "local-logistics-agent-mvp";

    private final JdbcTemplate jdbcTemplate;

    public AgentAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordTrace(String traceId, AgentUserContext context, AgentChatRequest request,
                            String answer, String riskLevel, long latencyMs) {
        jdbcTemplate.update("""
                INSERT INTO ai_agent_trace
                (trace_id, tenant_id, user_id, conversation_id, user_message, final_answer,
                 model_name, risk_level, latency_ms, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, traceId, context.tenantId(), context.userId(), request.conversationId(),
                request.message(), answer, MODEL_NAME, riskLevel, latencyMs, Timestamp.from(Instant.now()));
    }

    public void recordToolCalls(String traceId, List<ToolCallSummary> toolCalls) {
        for (ToolCallSummary toolCall : toolCalls) {
            jdbcTemplate.update("""
                    INSERT INTO ai_agent_tool_call
                    (trace_id, tool_name, arguments_json, result_summary, status, latency_ms, error_code, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, traceId, toolCall.tool(), "{}", toolCall.summary(), toolCall.status(),
                    toolCall.latencyMs(), toolCall.errorCode(), Timestamp.from(Instant.now()));
        }
    }

    public Optional<AuditResponse> findByTraceId(String traceId) {
        List<AuditResponse> rows = jdbcTemplate.query("""
                SELECT * FROM ai_agent_trace
                WHERE trace_id = ?
                ORDER BY created_at DESC
                LIMIT 1
                """, (rs, rowNum) -> mapTrace(rs, findToolCalls(traceId)), traceId);
        return rows.stream().findFirst();
    }

    public List<AuditResponse> search(String tenantId, String userId, String customerId,
                                      LocalDate from, LocalDate to, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM ai_agent_trace
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (tenantId != null && !tenantId.isBlank()) {
            sql.append(" AND tenant_id = ?");
            args.add(tenantId);
        }
        if (userId != null && !userId.isBlank()) {
            sql.append(" AND user_id = ?");
            args.add(userId);
        }
        if (customerId != null && !customerId.isBlank()) {
            sql.append(" AND user_message LIKE ?");
            args.add("%" + customerId + "%");
        }
        if (from != null) {
            sql.append(" AND created_at >= ?");
            args.add(Timestamp.valueOf(from.atStartOfDay()));
        }
        if (to != null) {
            sql.append(" AND created_at < ?");
            args.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 100)));

        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> mapTrace(rs, findToolCalls(rs.getString("trace_id"))),
                args.toArray());
    }

    private List<ToolCallSummary> findToolCalls(String traceId) {
        return jdbcTemplate.query("""
                SELECT * FROM ai_agent_tool_call
                WHERE trace_id = ?
                ORDER BY id ASC
                """, this::mapToolCall, traceId);
    }

    private AuditResponse mapTrace(ResultSet rs, List<ToolCallSummary> toolCalls) throws SQLException {
        return new AuditResponse(
                rs.getString("trace_id"),
                rs.getString("tenant_id"),
                rs.getString("user_id"),
                rs.getString("conversation_id"),
                rs.getString("user_message"),
                rs.getString("final_answer"),
                rs.getString("risk_level"),
                rs.getLong("latency_ms"),
                toInstant(rs.getTimestamp("created_at")),
                toolCalls
        );
    }

    private ToolCallSummary mapToolCall(ResultSet rs, int rowNum) throws SQLException {
        return new ToolCallSummary(
                rs.getString("tool_name"),
                rs.getString("status"),
                rs.getString("result_summary"),
                rs.getLong("latency_ms"),
                rs.getString("error_code")
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        LocalDateTime localDateTime = timestamp.toLocalDateTime();
        return localDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant();
    }
}
