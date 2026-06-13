package com.superagent.logistics.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.logistics.agent.LogisticsAgentService;
import com.superagent.logistics.api.dto.AgentChatRequest;
import com.superagent.logistics.api.dto.AgentChatResponse;
import com.superagent.logistics.api.dto.AgentConversationDetail;
import com.superagent.logistics.api.dto.AgentConversationSummary;
import com.superagent.logistics.api.dto.AgentMessageFeedbackRequest;
import com.superagent.logistics.api.dto.AgentMessageFeedbackResponse;
import com.superagent.logistics.api.dto.AgentMessageResponse;
import com.superagent.logistics.api.dto.Citation;
import com.superagent.logistics.api.dto.ToolCallSummary;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class AgentConversationService {

    private static final TypeReference<List<Citation>> CITATION_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<ToolCallSummary>> TOOL_CALL_LIST = new TypeReference<>() {
    };

    private final LogisticsAgentService agentService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AgentConversationService(LogisticsAgentService agentService,
                                    JdbcTemplate jdbcTemplate,
                                    ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public AgentChatResponse chat(AgentChatRequest request) {
        long started = System.nanoTime();
        AgentChatRequest normalizedRequest = normalizeRequest(request);
        AgentUserContext context = AgentUserContext.from(
                normalizedRequest.tenantId(),
                normalizedRequest.userId(),
                normalizedRequest.roles()
        );

        String userMessageId = newId("msg-user");
        Instant userMessageAt = Instant.now();
        AgentChatResponse rawResponse = agentService.chat(normalizedRequest);
        long latencyMs = (System.nanoTime() - started) / 1_000_000;
        AgentChatResponse response = ensureMessageId(rawResponse);
        Instant assistantMessageAt = response.createdAt() == null ? Instant.now() : response.createdAt();

        upsertConversation(context, normalizedRequest, response, assistantMessageAt);
        insertMessage(context.tenantId(), response.conversationId(), userMessageId, "USER",
                normalizedRequest.message(), null, null, null, List.of(), List.of(), null, userMessageAt);
        insertMessage(context.tenantId(), response.conversationId(), response.messageId(), "ASSISTANT",
                response.answer(), response.traceId(), response.riskLevel(), response.confidence(),
                response.citations(), response.toolCalls(), latencyMs, assistantMessageAt);
        return response;
    }

    public SseEmitter chatStream(AgentChatRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L);
        CompletableFuture.runAsync(() -> {
            try {
                sendEvent(emitter, "status", Map.of("message", "已接收问题"));
                sendEvent(emitter, "status", Map.of("message", "正在查询业务数据和知识库"));
                AgentChatResponse response = chat(request);
                sendEvent(emitter, "status", Map.of("message", "正在生成回答"));
                for (String chunk : answerChunks(response.answer())) {
                    sendEvent(emitter, "delta", Map.of("delta", chunk));
                }
                sendEvent(emitter, "complete", response);
                emitter.complete();
            } catch (RuntimeException | IOException ex) {
                try {
                    sendEvent(emitter, "error", Map.of("message", ex.getMessage()));
                    emitter.complete();
                } catch (RuntimeException | IOException sendEx) {
                    emitter.completeWithError(sendEx);
                }
            }
        });
        return emitter;
    }

    public List<AgentConversationSummary> listConversations(String tenantId, String userId, List<String> roles, int limit) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        return jdbcTemplate.query("""
                        SELECT * FROM ai_agent_conversation
                        WHERE tenant_id = ? AND user_id = ?
                        ORDER BY updated_at DESC
                        LIMIT ?
                        """,
                this::mapSummary,
                context.tenantId(),
                context.userId(),
                Math.max(1, Math.min(limit, 100)));
    }

    public AgentConversationDetail getConversation(String conversationId, String tenantId, String userId, List<String> roles) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        AgentConversationSummary summary = findConversation(context.tenantId(), conversationId)
                .filter(row -> row.userId().equals(context.userId()) || context.hasAnyRole("ADMIN", "OPS_MANAGER"))
                .orElseThrow(() -> new IllegalArgumentException("未找到会话 " + conversationId));
        List<AgentMessageResponse> messages = jdbcTemplate.query("""
                        SELECT * FROM ai_agent_message
                        WHERE tenant_id = ? AND conversation_id = ?
                        ORDER BY created_at ASC, id ASC
                        """,
                this::mapMessage,
                context.tenantId(),
                conversationId);
        return new AgentConversationDetail(
                summary.tenantId(),
                summary.conversationId(),
                summary.userId(),
                summary.title(),
                summary.lastMessage(),
                summary.lastTraceId(),
                summary.lastRiskLevel(),
                summary.messageCount(),
                summary.createdAt(),
                summary.updatedAt(),
                messages
        );
    }

    public AgentMessageFeedbackResponse feedback(String messageId, AgentMessageFeedbackRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        AgentMessageResponse message = findMessage(context.tenantId(), messageId)
                .orElseThrow(() -> new IllegalArgumentException("未找到消息 " + messageId));
        String rating = normalizeRating(request.rating());
        String feedbackId = newId("fb");
        String conversationId = firstNonBlank(request.conversationId(), findConversationId(context.tenantId(), messageId));
        String traceId = firstNonBlank(request.traceId(), message.traceId());
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        INSERT INTO ai_agent_message_feedback
                        (tenant_id, feedback_id, conversation_id, message_id, trace_id, user_id, rating, reason, comment, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                context.tenantId(),
                feedbackId,
                conversationId,
                messageId,
                traceId,
                context.userId(),
                rating,
                blankToNull(request.reason()),
                blankToNull(request.comment()),
                Timestamp.from(now));
        return new AgentMessageFeedbackResponse(
                feedbackId,
                context.tenantId(),
                conversationId,
                messageId,
                traceId,
                context.userId(),
                rating,
                blankToNull(request.reason()),
                blankToNull(request.comment()),
                now
        );
    }

    private AgentChatRequest normalizeRequest(AgentChatRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? newId("conv-web")
                : request.conversationId();
        return new AgentChatRequest(
                conversationId,
                context.userId(),
                context.tenantId(),
                List.copyOf(context.roles()),
                request.message(),
                request.returnCitations()
        );
    }

    private AgentChatResponse ensureMessageId(AgentChatResponse response) {
        if (response.messageId() != null && !response.messageId().isBlank()) {
            return response;
        }
        return new AgentChatResponse(
                response.traceId(),
                response.conversationId(),
                newId("msg-ai"),
                response.answer(),
                response.riskLevel(),
                response.confidence(),
                response.citations(),
                response.toolCalls(),
                response.createdAt()
        );
    }

    private void upsertConversation(AgentUserContext context, AgentChatRequest request,
                                    AgentChatResponse response, Instant updatedAt) {
        int updated = jdbcTemplate.update("""
                        UPDATE ai_agent_conversation
                        SET user_id = ?, title = ?, roles = ?, last_message = ?, last_trace_id = ?,
                            last_risk_level = ?, message_count = message_count + 2, updated_at = ?
                        WHERE tenant_id = ? AND conversation_id = ?
                        """,
                context.userId(),
                titleFrom(request.message()),
                String.join(",", context.roles()),
                request.message(),
                response.traceId(),
                response.riskLevel(),
                Timestamp.from(updatedAt),
                context.tenantId(),
                response.conversationId());
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO ai_agent_conversation
                        (tenant_id, conversation_id, user_id, title, roles, last_message, last_trace_id,
                         last_risk_level, message_count, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                context.tenantId(),
                response.conversationId(),
                context.userId(),
                titleFrom(request.message()),
                String.join(",", context.roles()),
                request.message(),
                response.traceId(),
                response.riskLevel(),
                2,
                Timestamp.from(updatedAt),
                Timestamp.from(updatedAt));
    }

    private void insertMessage(String tenantId, String conversationId, String messageId, String role,
                               String content, String traceId, String riskLevel, Double confidence,
                               List<Citation> citations, List<ToolCallSummary> toolCalls,
                               Long latencyMs, Instant createdAt) {
        jdbcTemplate.update("""
                        INSERT INTO ai_agent_message
                        (tenant_id, conversation_id, message_id, role, content, trace_id, risk_level,
                         confidence, citations_json, tool_calls_json, latency_ms, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                tenantId,
                conversationId,
                messageId,
                role,
                content,
                traceId,
                riskLevel,
                confidence,
                toJson(citations),
                toJson(toolCalls),
                latencyMs,
                Timestamp.from(createdAt));
    }

    private Optional<AgentConversationSummary> findConversation(String tenantId, String conversationId) {
        List<AgentConversationSummary> rows = jdbcTemplate.query("""
                        SELECT * FROM ai_agent_conversation
                        WHERE tenant_id = ? AND conversation_id = ?
                        """,
                this::mapSummary,
                tenantId,
                conversationId);
        return rows.stream().findFirst();
    }

    private Optional<AgentMessageResponse> findMessage(String tenantId, String messageId) {
        List<AgentMessageResponse> rows = jdbcTemplate.query("""
                        SELECT * FROM ai_agent_message
                        WHERE tenant_id = ? AND message_id = ?
                        """,
                this::mapMessage,
                tenantId,
                messageId);
        return rows.stream().findFirst();
    }

    private String findConversationId(String tenantId, String messageId) {
        List<String> rows = jdbcTemplate.query("""
                        SELECT conversation_id FROM ai_agent_message
                        WHERE tenant_id = ? AND message_id = ?
                        """,
                (rs, rowNum) -> rs.getString("conversation_id"),
                tenantId,
                messageId);
        return rows.stream().findFirst().orElse(null);
    }

    private AgentConversationSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new AgentConversationSummary(
                rs.getString("tenant_id"),
                rs.getString("conversation_id"),
                rs.getString("user_id"),
                rs.getString("title"),
                rs.getString("last_message"),
                rs.getString("last_trace_id"),
                rs.getString("last_risk_level"),
                rs.getInt("message_count"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private AgentMessageResponse mapMessage(ResultSet rs, int rowNum) throws SQLException {
        Double confidence = rs.getObject("confidence") == null ? null : rs.getDouble("confidence");
        return new AgentMessageResponse(
                rs.getString("message_id"),
                rs.getString("role"),
                rs.getString("content"),
                rs.getString("trace_id"),
                rs.getString("risk_level"),
                confidence,
                readJsonList(rs.getString("citations_json"), CITATION_LIST),
                readJsonList(rs.getString("tool_calls_json"), TOOL_CALL_LIST),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化会话消息失败", ex);
        }
    }

    private <T> List<T> readJsonList(String json, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private List<String> answerChunks(String answer) {
        if (answer == null || answer.isBlank()) {
            return List.of("");
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int chunkSize = 120;
        while (start < answer.length()) {
            int end = Math.min(answer.length(), start + chunkSize);
            chunks.add(answer.substring(start, end));
            start = end;
        }
        return chunks;
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    private String normalizeRating(String rating) {
        String normalized = rating == null ? "" : rating.trim().toUpperCase();
        if (!"HELPFUL".equals(normalized) && !"NOT_HELPFUL".equals(normalized)) {
            throw new IllegalArgumentException("rating 只能是 HELPFUL 或 NOT_HELPFUL");
        }
        return normalized;
    }

    private String titleFrom(String message) {
        String normalized = message == null ? "新会话" : message.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "新会话";
        }
        return normalized.length() <= 48 ? normalized : normalized.substring(0, 48) + "...";
    }

    private String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 12);
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
