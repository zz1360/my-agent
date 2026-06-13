package com.superagent.logistics.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.logistics.api.dto.AgentFeedbackSampleResponse;
import com.superagent.logistics.api.dto.Citation;
import com.superagent.logistics.api.dto.EvalCaseCandidateCreateRequest;
import com.superagent.logistics.api.dto.EvalCaseCandidatePromoteRequest;
import com.superagent.logistics.api.dto.EvalCaseCandidateResponse;
import com.superagent.logistics.api.dto.FeedbackRagExperimentResponse;
import com.superagent.logistics.api.dto.RagExperimentRequest;
import com.superagent.logistics.api.dto.RagExperimentResponse;
import com.superagent.logistics.api.dto.RagExperimentRunResponse;
import com.superagent.logistics.api.dto.ToolCallSummary;
import com.superagent.logistics.knowledge.RagExperimentService;
import com.superagent.logistics.security.AccessDeniedException;
import com.superagent.logistics.security.AgentPermissionService;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FeedbackLearningService {

    private static final TypeReference<List<Citation>> CITATION_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<ToolCallSummary>> TOOL_CALL_LIST = new TypeReference<>() {
    };
    private static final Pattern BUSINESS_ID_PATTERN = Pattern.compile("\\b(C\\d{3}|WB[A-Z0-9]{8,24})\\b", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AgentPermissionService permissionService;
    private final RagExperimentService ragExperimentService;

    public FeedbackLearningService(JdbcTemplate jdbcTemplate,
                                   ObjectMapper objectMapper,
                                   AgentPermissionService permissionService,
                                   RagExperimentService ragExperimentService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.permissionService = permissionService;
        this.ragExperimentService = ragExperimentService;
    }

    public List<AgentFeedbackSampleResponse> listFeedback(String tenantId, String userId, List<String> roles,
                                                          String rating, String reason, boolean unconvertedOnly,
                                                          int limit) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        StringBuilder sql = new StringBuilder("""
                SELECT f.*, m.content AS source_answer, m.citations_json, m.tool_calls_json, m.created_at AS message_created_at,
                       c.title AS conversation_title, cand.candidate_id, cand.status AS candidate_status
                FROM ai_agent_message_feedback f
                JOIN ai_agent_message m ON f.tenant_id = m.tenant_id AND f.message_id = m.message_id
                LEFT JOIN ai_agent_conversation c ON f.tenant_id = c.tenant_id AND f.conversation_id = c.conversation_id
                LEFT JOIN ai_eval_case_candidate cand ON f.tenant_id = cand.tenant_id AND f.feedback_id = cand.feedback_id
                WHERE f.tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(context.tenantId());
        if (!context.hasAnyRole("ADMIN", "OPS_MANAGER", "OPERATIONS")) {
            sql.append(" AND f.user_id = ?");
            args.add(context.userId());
        }
        if (rating != null && !rating.isBlank()) {
            sql.append(" AND f.rating = ?");
            args.add(rating.trim().toUpperCase(Locale.ROOT));
        }
        if (reason != null && !reason.isBlank()) {
            sql.append(" AND f.reason = ?");
            args.add(reason.trim());
        }
        if (unconvertedOnly) {
            sql.append(" AND cand.candidate_id IS NULL");
        }
        sql.append(" ORDER BY f.created_at DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 100)));
        return jdbcTemplate.query(sql.toString(), this::mapFeedback, args.toArray());
    }

    public List<EvalCaseCandidateResponse> listCandidates(String tenantId, String userId, List<String> roles,
                                                          String status, int limit) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        checkFeedbackMaintainer(context);
        StringBuilder sql = new StringBuilder("""
                SELECT *
                FROM ai_eval_case_candidate
                WHERE tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(context.tenantId());
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY updated_at DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 100)));
        return jdbcTemplate.query(sql.toString(), this::mapCandidate, args.toArray());
    }

    public EvalCaseCandidateResponse createCandidate(String feedbackId, EvalCaseCandidateCreateRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        permissionService.checkBusinessReadable(context);
        checkFeedbackMaintainer(context);
        AgentFeedbackSampleResponse feedback = findFeedback(context, feedbackId)
                .orElseThrow(() -> new IllegalArgumentException("未找到反馈：" + feedbackId));
        if (!"NOT_HELPFUL".equals(feedback.rating())) {
            throw new IllegalArgumentException("只有 NOT_HELPFUL 反馈会进入评测候选池");
        }
        Optional<EvalCaseCandidateResponse> existing = findCandidateByFeedback(context.tenantId(), feedbackId);
        if (existing.isPresent()) {
            return existing.get();
        }

        String evalType = resolveEvalType(request.evalType(), feedback.reason());
        String endpoint = resolveEndpoint(request.endpoint(), evalType);
        List<String> expectedCitations = overrideOrDefault(request.expectedCitations(), feedback.citations().stream().map(Citation::docId).toList());
        List<String> expectedRagDocIds = overrideOrDefault(request.expectedRagDocIds(), expectedCitations);
        List<String> expectedRagChunkIds = overrideOrDefault(request.expectedRagChunkIds(), feedback.citations().stream().map(Citation::chunkId).toList());
        List<String> expectedContains = overrideOrDefault(request.expectedContains(), inferExpectedContains(feedback.sourceQuestion(), feedback.sourceAnswer()));
        int expectedMinToolCalls = request.expectedMinToolCalls() == null
                ? Math.max(0, feedback.toolCalls().size())
                : Math.max(0, request.expectedMinToolCalls());
        int expectedTopK = request.expectedTopK() == null ? 5 : Math.max(1, Math.min(request.expectedTopK(), 20));
        String ragQuery = firstNonBlank(request.ragQuery(), feedback.sourceQuestion());
        String candidateId = "cand-" + UUID.randomUUID().toString().substring(0, 10);
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        INSERT INTO ai_eval_case_candidate
                        (tenant_id, candidate_id, feedback_id, conversation_id, message_id, trace_id, source_question,
                         source_answer, rating, reason, endpoint, eval_type, expected_contains, expected_citations,
                         expected_rag_doc_ids, expected_rag_chunk_ids, expected_min_tool_calls, expected_top_k,
                         rag_query, status, eval_case_id, rag_experiment_id, created_by, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                context.tenantId(),
                candidateId,
                feedback.feedbackId(),
                feedback.conversationId(),
                feedback.messageId(),
                feedback.traceId(),
                feedback.sourceQuestion(),
                feedback.sourceAnswer(),
                feedback.rating(),
                feedback.reason(),
                endpoint,
                evalType,
                joinLines(expectedContains),
                joinLines(expectedCitations),
                joinLines(expectedRagDocIds),
                joinLines(expectedRagChunkIds),
                expectedMinToolCalls,
                expectedTopK,
                ragQuery,
                "CANDIDATE",
                null,
                null,
                context.userId(),
                Timestamp.from(now),
                Timestamp.from(now));
        return getCandidate(context.tenantId(), candidateId);
    }

    public EvalCaseCandidateResponse promoteToEvalCase(String candidateId, EvalCaseCandidatePromoteRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        permissionService.checkBusinessReadable(context);
        checkFeedbackMaintainer(context);
        EvalCaseCandidateResponse candidate = getCandidate(context.tenantId(), candidateId);
        String caseId = firstNonBlank(request.caseId(), "feedback-" + candidate.candidateId().replace("cand-", ""));
        List<String> expectedContains = overrideOrDefault(request.expectedContains(), candidate.expectedContains());
        List<String> expectedCitations = overrideOrDefault(request.expectedCitations(), candidate.expectedCitations());
        List<String> expectedRagDocIds = overrideOrDefault(request.expectedRagDocIds(), candidate.expectedRagDocIds());
        List<String> expectedRagChunkIds = overrideOrDefault(request.expectedRagChunkIds(), candidate.expectedRagChunkIds());
        int expectedMinToolCalls = request.expectedMinToolCalls() == null ? candidate.expectedMinToolCalls() : Math.max(0, request.expectedMinToolCalls());
        int expectedTopK = request.expectedTopK() == null ? candidate.expectedTopK() : Math.max(1, Math.min(request.expectedTopK(), 20));
        String ragQuery = firstNonBlank(request.ragQuery(), candidate.ragQuery());
        String requestJson = requestJson(context, candidate, expectedTopK, ragQuery);
        Instant now = Instant.now();
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ai_eval_case
                WHERE tenant_id = ? AND case_id = ?
                """, Integer.class, context.tenantId(), caseId);
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                            INSERT INTO ai_eval_case
                            (tenant_id, case_id, name, endpoint, eval_type, user_id, roles, request_json, expected_contains,
                             expected_citations, expected_rag_doc_ids, expected_rag_chunk_ids, expected_min_tool_calls,
                             expected_top_k, rag_query, risk_level, enabled, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    context.tenantId(),
                    caseId,
                    firstNonBlank(request.name(), "反馈样本：" + excerpt(candidate.sourceQuestion(), 48)),
                    candidate.endpoint(),
                    candidate.evalType(),
                    context.userId(),
                    String.join(",", context.roles()),
                    requestJson,
                    joinLines(expectedContains),
                    joinLines(expectedCitations),
                    joinLines(expectedRagDocIds),
                    joinLines(expectedRagChunkIds),
                    expectedMinToolCalls,
                    expectedTopK,
                    ragQuery,
                    blankToNull(request.riskLevel()),
                    Boolean.TRUE.equals(request.enabled()),
                    Timestamp.from(now),
                    Timestamp.from(now));
        }
        jdbcTemplate.update("""
                        UPDATE ai_eval_case_candidate
                        SET status = ?, eval_case_id = ?, updated_at = ?
                        WHERE tenant_id = ? AND candidate_id = ?
                        """,
                "EVAL_CASE_CREATED",
                caseId,
                Timestamp.from(now),
                context.tenantId(),
                candidateId);
        return getCandidate(context.tenantId(), candidateId);
    }

    public FeedbackRagExperimentResponse createRagExperiment(String candidateId, String tenantId, String userId,
                                                             List<String> roles, boolean runNow) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        checkFeedbackMaintainer(context);
        EvalCaseCandidateResponse candidate = getCandidate(context.tenantId(), candidateId);
        if (candidate.expectedRagDocIds().isEmpty() && candidate.expectedRagChunkIds().isEmpty()) {
            throw new IllegalArgumentException("候选样本缺少期望引用，无法创建 RAG 实验");
        }
        String experimentId = candidate.ragExperimentId() == null || candidate.ragExperimentId().isBlank()
                ? "raglab-fb-" + candidate.candidateId().replace("cand-", "")
                : candidate.ragExperimentId();
        RagExperimentResponse experiment = ragExperimentService.upsert(new RagExperimentRequest(
                context.tenantId(),
                context.userId(),
                new ArrayList<>(context.roles()),
                experimentId,
                "反馈样本 RAG 对比：" + excerpt(candidate.sourceQuestion(), 42),
                "由反馈 " + candidate.feedbackId() + " 自动生成，用于诊断引用或检索质量问题。",
                firstNonBlank(candidate.ragQuery(), candidate.sourceQuestion()),
                candidate.expectedRagDocIds(),
                candidate.expectedRagChunkIds(),
                candidate.expectedTopK(),
                List.of("KEYWORD_ONLY", "HYBRID_RULE", "HYBRID_RERANKER"),
                true
        ));
        List<RagExperimentRunResponse> runs = runNow
                ? ragExperimentService.runExperiment(context.tenantId(), context.userId(), new ArrayList<>(context.roles()), experiment.experimentId(), null)
                : List.of();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        UPDATE ai_eval_case_candidate
                        SET status = ?, rag_experiment_id = ?, updated_at = ?
                        WHERE tenant_id = ? AND candidate_id = ?
                        """,
                "RAG_EXPERIMENT_CREATED",
                experiment.experimentId(),
                Timestamp.from(now),
                context.tenantId(),
                candidateId);
        return new FeedbackRagExperimentResponse(getCandidate(context.tenantId(), candidateId), experiment, runs);
    }

    private Optional<AgentFeedbackSampleResponse> findFeedback(AgentUserContext context, String feedbackId) {
        List<AgentFeedbackSampleResponse> rows = jdbcTemplate.query("""
                        SELECT f.*, m.content AS source_answer, m.citations_json, m.tool_calls_json, m.created_at AS message_created_at,
                               c.title AS conversation_title, cand.candidate_id, cand.status AS candidate_status
                        FROM ai_agent_message_feedback f
                        JOIN ai_agent_message m ON f.tenant_id = m.tenant_id AND f.message_id = m.message_id
                        LEFT JOIN ai_agent_conversation c ON f.tenant_id = c.tenant_id AND f.conversation_id = c.conversation_id
                        LEFT JOIN ai_eval_case_candidate cand ON f.tenant_id = cand.tenant_id AND f.feedback_id = cand.feedback_id
                        WHERE f.tenant_id = ? AND f.feedback_id = ?
                        """,
                this::mapFeedback,
                context.tenantId(),
                feedbackId);
        return rows.stream()
                .filter(row -> context.hasAnyRole("ADMIN", "OPS_MANAGER", "OPERATIONS") || context.userId().equals(row.userId()))
                .findFirst();
    }

    private void checkFeedbackMaintainer(AgentUserContext context) {
        if (!context.hasAnyRole("ADMIN", "OPS_MANAGER", "OPERATIONS")) {
            throw new AccessDeniedException("当前用户没有反馈评测维护权限");
        }
    }

    private Optional<EvalCaseCandidateResponse> findCandidateByFeedback(String tenantId, String feedbackId) {
        return jdbcTemplate.query("""
                        SELECT * FROM ai_eval_case_candidate
                        WHERE tenant_id = ? AND feedback_id = ?
                        """,
                this::mapCandidate,
                tenantId,
                feedbackId).stream().findFirst();
    }

    private EvalCaseCandidateResponse getCandidate(String tenantId, String candidateId) {
        return jdbcTemplate.query("""
                        SELECT * FROM ai_eval_case_candidate
                        WHERE tenant_id = ? AND candidate_id = ?
                        """,
                this::mapCandidate,
                tenantId,
                candidateId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到评测候选：" + candidateId));
    }

    private AgentFeedbackSampleResponse mapFeedback(ResultSet rs, int rowNum) throws SQLException {
        String tenantId = rs.getString("tenant_id");
        String conversationId = rs.getString("conversation_id");
        Timestamp messageCreatedAt = rs.getTimestamp("message_created_at");
        return new AgentFeedbackSampleResponse(
                rs.getString("feedback_id"),
                tenantId,
                conversationId,
                rs.getString("conversation_title"),
                rs.getString("message_id"),
                rs.getString("trace_id"),
                rs.getString("user_id"),
                rs.getString("rating"),
                rs.getString("reason"),
                rs.getString("comment"),
                findPreviousUserQuestion(tenantId, conversationId, messageCreatedAt),
                rs.getString("source_answer"),
                readJsonList(rs.getString("citations_json"), CITATION_LIST),
                readJsonList(rs.getString("tool_calls_json"), TOOL_CALL_LIST),
                rs.getString("candidate_id"),
                rs.getString("candidate_status"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private EvalCaseCandidateResponse mapCandidate(ResultSet rs, int rowNum) throws SQLException {
        return new EvalCaseCandidateResponse(
                rs.getString("tenant_id"),
                rs.getString("candidate_id"),
                rs.getString("feedback_id"),
                rs.getString("conversation_id"),
                rs.getString("message_id"),
                rs.getString("trace_id"),
                rs.getString("source_question"),
                rs.getString("source_answer"),
                rs.getString("rating"),
                rs.getString("reason"),
                rs.getString("endpoint"),
                rs.getString("eval_type"),
                splitLines(rs.getString("expected_contains")),
                splitLines(rs.getString("expected_citations")),
                splitLines(rs.getString("expected_rag_doc_ids")),
                splitLines(rs.getString("expected_rag_chunk_ids")),
                rs.getInt("expected_min_tool_calls"),
                rs.getInt("expected_top_k"),
                rs.getString("rag_query"),
                rs.getString("status"),
                rs.getString("eval_case_id"),
                rs.getString("rag_experiment_id"),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private String findPreviousUserQuestion(String tenantId, String conversationId, Timestamp before) {
        if (before == null) {
            return "";
        }
        List<String> rows = jdbcTemplate.query("""
                        SELECT content
                        FROM ai_agent_message
                        WHERE tenant_id = ? AND conversation_id = ? AND role = 'USER' AND created_at <= ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> rs.getString("content"),
                tenantId,
                conversationId,
                before);
        return rows.stream().findFirst().orElse("");
    }

    private String requestJson(AgentUserContext context, EvalCaseCandidateResponse candidate,
                               int expectedTopK, String ragQuery) {
        try {
            if ("RAG".equals(candidate.evalType())) {
                return objectMapper.writeValueAsString(Map.of(
                        "tenantId", context.tenantId(),
                        "userId", context.userId(),
                        "roles", new ArrayList<>(context.roles()),
                        "query", firstNonBlank(ragQuery, candidate.sourceQuestion()),
                        "topK", expectedTopK
                ));
            }
            return objectMapper.writeValueAsString(Map.of(
                    "conversationId", "conv-eval-" + candidate.candidateId(),
                    "tenantId", context.tenantId(),
                    "userId", context.userId(),
                    "roles", new ArrayList<>(context.roles()),
                    "message", firstNonBlank(candidate.sourceQuestion(), candidate.sourceAnswer()),
                    "returnCitations", true
            ));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("生成评测请求失败", ex);
        }
    }

    private String resolveEvalType(String requestValue, String reason) {
        if (requestValue != null && !requestValue.isBlank()) {
            String value = requestValue.trim().toUpperCase(Locale.ROOT);
            if (!"AGENT".equals(value) && !"RAG".equals(value)) {
                throw new IllegalArgumentException("evalType 只能是 AGENT 或 RAG");
            }
            return value;
        }
        return "CITATION_WEAK".equalsIgnoreCase(reason) ? "RAG" : "AGENT";
    }

    private String resolveEndpoint(String requestValue, String evalType) {
        if (requestValue != null && !requestValue.isBlank()) {
            return requestValue.trim();
        }
        return "RAG".equals(evalType) ? "rag-search" : "chat";
    }

    private List<String> inferExpectedContains(String question, String answer) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        String source = (question == null ? "" : question) + "\n" + (answer == null ? "" : answer);
        Matcher matcher = BUSINESS_ID_PATTERN.matcher(source);
        while (matcher.find() && values.size() < 3) {
            values.add(matcher.group(1).toUpperCase(Locale.ROOT));
        }
        if (source.contains("人工复核")) {
            values.add("人工复核");
        }
        if (values.isEmpty() && question != null && !question.isBlank()) {
            values.add(excerpt(question, 24));
        }
        return new ArrayList<>(values);
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

    private List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
    }

    private List<String> overrideOrDefault(List<String> requested, List<String> defaults) {
        List<String> source = requested == null || requested.isEmpty() ? defaults : requested;
        return distinct(source);
    }

    private List<String> distinct(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                set.add(value.trim());
            }
        }
        return new ArrayList<>(set);
    }

    private String joinLines(List<String> values) {
        return String.join("\n", distinct(values));
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String excerpt(String value, int maxLength) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }
}
