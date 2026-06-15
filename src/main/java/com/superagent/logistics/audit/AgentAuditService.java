package com.superagent.logistics.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.logistics.api.dto.AgentChatRequest;
import com.superagent.logistics.api.dto.AuditResponse;
import com.superagent.logistics.api.dto.KnowledgeSearchHitResponse;
import com.superagent.logistics.api.dto.RagAuditResponse;
import com.superagent.logistics.api.dto.ToolCallSummary;
import com.superagent.logistics.knowledge.KnowledgeSearchDiagnostics;
import com.superagent.logistics.knowledge.KnowledgeSearchResult;
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
    private static final TypeReference<List<KnowledgeSearchHitResponse>> KNOWLEDGE_HIT_LIST = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AgentAuditService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
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

    public void recordRagAudit(String traceId, AgentUserContext context, KnowledgeSearchDiagnostics diagnostics) {
        if (diagnostics == null) {
            return;
        }
        List<KnowledgeSearchHitResponse> hits = diagnostics.results().stream()
                .map(this::toHitResponse)
                .toList();
        jdbcTemplate.update("""
                INSERT INTO ai_agent_rag_audit
                (trace_id, tenant_id, query_text, retrieval_mode, knowledge_version, top_k,
                 vector_ready, vector_used, keyword_used, reranker_used, active_chunk_count,
                 candidate_count, rerank_candidate_count, returned_count, hits_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                traceId,
                context.tenantId(),
                diagnostics.query(),
                diagnostics.retrievalMode(),
                diagnostics.knowledgeVersion(),
                diagnostics.requestedTopK(),
                diagnostics.vectorReady(),
                diagnostics.vectorUsed(),
                diagnostics.keywordUsed(),
                diagnostics.rerankerUsed(),
                diagnostics.activeChunkCount(),
                diagnostics.candidateCount(),
                diagnostics.rerankCandidateCount(),
                diagnostics.returnedCount(),
                toJson(hits),
                Timestamp.from(Instant.now()));
    }

    public Optional<AuditResponse> findByTraceId(String traceId) {
        List<AuditResponse> rows = jdbcTemplate.query("""
                SELECT * FROM ai_agent_trace
                WHERE trace_id = ?
                ORDER BY created_at DESC
                LIMIT 1
                """, (rs, rowNum) -> mapTrace(rs, findToolCalls(traceId), findRagAudits(traceId)), traceId);
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
                (rs, rowNum) -> {
                    String traceId = rs.getString("trace_id");
                    return mapTrace(rs, findToolCalls(traceId), findRagAudits(traceId));
                },
                args.toArray());
    }

    private List<ToolCallSummary> findToolCalls(String traceId) {
        return jdbcTemplate.query("""
                SELECT * FROM ai_agent_tool_call
                WHERE trace_id = ?
                ORDER BY id ASC
                """, this::mapToolCall, traceId);
    }

    private List<RagAuditResponse> findRagAudits(String traceId) {
        return jdbcTemplate.query("""
                SELECT * FROM ai_agent_rag_audit
                WHERE trace_id = ?
                ORDER BY id ASC
                """, this::mapRagAudit, traceId);
    }

    private AuditResponse mapTrace(ResultSet rs, List<ToolCallSummary> toolCalls,
                                   List<RagAuditResponse> ragAudits) throws SQLException {
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
                toolCalls,
                ragAudits
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

    private RagAuditResponse mapRagAudit(ResultSet rs, int rowNum) throws SQLException {
        return new RagAuditResponse(
                rs.getString("trace_id"),
                rs.getString("retrieval_mode"),
                rs.getString("knowledge_version"),
                rs.getInt("top_k"),
                rs.getBoolean("vector_ready"),
                rs.getBoolean("vector_used"),
                rs.getBoolean("keyword_used"),
                rs.getBoolean("reranker_used"),
                rs.getInt("active_chunk_count"),
                rs.getInt("candidate_count"),
                rs.getInt("rerank_candidate_count"),
                rs.getInt("returned_count"),
                readHits(rs.getString("hits_json")),
                toInstant(rs.getTimestamp("created_at"))
        );
    }

    private KnowledgeSearchHitResponse toHitResponse(KnowledgeSearchResult result) {
        return new KnowledgeSearchHitResponse(
                result.chunk().title(),
                result.chunk().docId(),
                result.chunk().chunkId(),
                excerpt(result.chunk().content(), 160),
                result.score(),
                result.vectorScore(),
                result.keywordScore(),
                result.ruleScore(),
                result.rerankerScore(),
                result.rerankerProvider()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("审计 JSON 序列化失败", ex);
        }
    }

    private List<KnowledgeSearchHitResponse> readHits(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, KNOWLEDGE_HIT_LIST);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        LocalDateTime localDateTime = timestamp.toLocalDateTime();
        return localDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant();
    }

    private String excerpt(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        String compact = content.replaceAll("\\s+", " ").trim();
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, maxLength) + "...";
    }
}
