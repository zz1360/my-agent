package com.superagent.logistics.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.logistics.api.dto.AgentFeedbackSampleResponse;
import com.superagent.logistics.api.dto.Citation;
import com.superagent.logistics.api.dto.EvalCaseCandidateAnnotateRequest;
import com.superagent.logistics.api.dto.EvalCaseCandidateCreateRequest;
import com.superagent.logistics.api.dto.EvalCaseCandidatePromoteRequest;
import com.superagent.logistics.api.dto.EvalCaseCandidateReviewRequest;
import com.superagent.logistics.api.dto.EvalCaseCandidateResponse;
import com.superagent.logistics.api.dto.FeedbackCandidateAuditResponse;
import com.superagent.logistics.api.dto.FeedbackQualityMetricsResponse;
import com.superagent.logistics.api.dto.FeedbackQualityMetricsResponse.DailyQualityTrend;
import com.superagent.logistics.api.dto.FeedbackQualityMetricsResponse.MetricCount;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

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
        List<String> feedbackTags = defaultFeedbackTags(feedback.reason());
        String candidateId = "cand-" + UUID.randomUUID().toString().substring(0, 10);
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        INSERT INTO ai_eval_case_candidate
                        (tenant_id, candidate_id, feedback_id, conversation_id, message_id, trace_id, source_question,
                         source_answer, rating, reason, endpoint, eval_type, expected_contains, expected_citations,
                         expected_rag_doc_ids, expected_rag_chunk_ids, expected_min_tool_calls, expected_top_k,
                         rag_query, status, feedback_tags, annotation_note, review_status, reviewer_id, review_comment,
                         reviewed_at, annotated_by, annotated_at, eval_case_id, rag_experiment_id, created_by, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                joinLines(feedbackTags),
                null,
                "UNREVIEWED",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                context.userId(),
                Timestamp.from(now),
                Timestamp.from(now));
        EvalCaseCandidateResponse candidate = getCandidate(context.tenantId(), candidateId);
        writeCandidateAudit(context, candidate, "CANDIDATE_CREATED", "由负反馈生成评测候选", Map.of(
                "feedbackReason", firstNonBlank(feedback.reason(), "UNKNOWN"),
                "evalType", candidate.evalType(),
                "feedbackTags", candidate.feedbackTags()
        ));
        return candidate;
    }

    public EvalCaseCandidateResponse annotateCandidate(String candidateId, EvalCaseCandidateAnnotateRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        permissionService.checkBusinessReadable(context);
        checkFeedbackMaintainer(context);
        EvalCaseCandidateResponse current = getCandidate(context.tenantId(), candidateId);
        if ("REJECTED".equals(current.reviewStatus())) {
            throw new IllegalArgumentException("已驳回的评测候选不能继续标注");
        }
        EvalCaseCandidateResponse before = current;
        String evalType = request.evalType() == null || request.evalType().isBlank()
                ? current.evalType()
                : resolveEvalType(request.evalType(), current.reason());
        String endpoint = firstNonBlank(request.endpoint(), firstNonBlank(current.endpoint(), resolveEndpoint(null, evalType)));
        List<String> expectedContains = overrideOrDefault(request.expectedContains(), current.expectedContains());
        List<String> expectedCitations = overrideOrDefault(request.expectedCitations(), current.expectedCitations());
        List<String> expectedRagDocIds = overrideOrDefault(request.expectedRagDocIds(), current.expectedRagDocIds());
        List<String> expectedRagChunkIds = overrideOrDefault(request.expectedRagChunkIds(), current.expectedRagChunkIds());
        int expectedMinToolCalls = request.expectedMinToolCalls() == null ? current.expectedMinToolCalls() : Math.max(0, request.expectedMinToolCalls());
        int expectedTopK = request.expectedTopK() == null ? current.expectedTopK() : Math.max(1, Math.min(request.expectedTopK(), 20));
        String ragQuery = firstNonBlank(request.ragQuery(), current.ragQuery());
        List<String> feedbackTags = request.feedbackTags() == null ? current.feedbackTags() : distinct(request.feedbackTags());
        String annotationNote = request.annotationNote() == null ? current.annotationNote() : request.annotationNote();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        UPDATE ai_eval_case_candidate
                        SET endpoint = ?, eval_type = ?, expected_contains = ?, expected_citations = ?,
                            expected_rag_doc_ids = ?, expected_rag_chunk_ids = ?, expected_min_tool_calls = ?,
                            expected_top_k = ?, rag_query = ?, feedback_tags = ?, annotation_note = ?,
                            annotated_by = ?, annotated_at = ?, updated_at = ?
                        WHERE tenant_id = ? AND candidate_id = ?
                        """,
                endpoint,
                evalType,
                joinLines(expectedContains),
                joinLines(expectedCitations),
                joinLines(expectedRagDocIds),
                joinLines(expectedRagChunkIds),
                expectedMinToolCalls,
                expectedTopK,
                ragQuery,
                joinLines(feedbackTags),
                blankToNull(annotationNote),
                context.userId(),
                Timestamp.from(now),
                Timestamp.from(now),
                context.tenantId(),
                candidateId);
        EvalCaseCandidateResponse updated = getCandidate(context.tenantId(), candidateId);
        writeCandidateAudit(context, updated, "CANDIDATE_ANNOTATED", "保存人工标注与期望结果", auditDetails(before, updated, Map.of(
                "evalType", updated.evalType(),
                "expectedTopK", updated.expectedTopK(),
                "feedbackTags", updated.feedbackTags()
        )));
        return updated;
    }

    public EvalCaseCandidateResponse reviewCandidate(String candidateId, EvalCaseCandidateReviewRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        permissionService.checkBusinessReadable(context);
        checkFeedbackMaintainer(context);
        EvalCaseCandidateResponse before = getCandidate(context.tenantId(), candidateId);
        String reviewStatus = normalizeReviewStatus(request.reviewStatus());
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        UPDATE ai_eval_case_candidate
                        SET review_status = ?, reviewer_id = ?, review_comment = ?, reviewed_at = ?, updated_at = ?
                        WHERE tenant_id = ? AND candidate_id = ?
                        """,
                reviewStatus,
                context.userId(),
                blankToNull(request.comment()),
                Timestamp.from(now),
                Timestamp.from(now),
                context.tenantId(),
                candidateId);
        EvalCaseCandidateResponse reviewed = getCandidate(context.tenantId(), candidateId);
        writeCandidateAudit(context, reviewed, reviewAuditType(reviewStatus), "候选审批状态变更为 " + reviewStatus, auditDetails(before, reviewed, Map.of(
                "reviewStatus", reviewStatus,
                "comment", firstNonBlank(request.comment(), "")
        )));
        return reviewed;
    }

    public EvalCaseCandidateResponse promoteToEvalCase(String candidateId, EvalCaseCandidatePromoteRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        permissionService.checkBusinessReadable(context);
        checkFeedbackMaintainer(context);
        EvalCaseCandidateResponse before = getCandidate(context.tenantId(), candidateId);
        if ("REJECTED".equals(before.reviewStatus())) {
            throw new IllegalArgumentException("已驳回的评测候选不能转为正式评测用例");
        }
        if (Boolean.TRUE.equals(request.enabled()) && !"APPROVED".equals(before.reviewStatus())) {
            throw new IllegalArgumentException("只有审批通过的评测候选才能启用为正式评测用例");
        }
        String caseId = firstNonBlank(request.caseId(), "feedback-" + before.candidateId().replace("cand-", ""));
        List<String> expectedContains = overrideOrDefault(request.expectedContains(), before.expectedContains());
        List<String> expectedCitations = overrideOrDefault(request.expectedCitations(), before.expectedCitations());
        List<String> expectedRagDocIds = overrideOrDefault(request.expectedRagDocIds(), before.expectedRagDocIds());
        List<String> expectedRagChunkIds = overrideOrDefault(request.expectedRagChunkIds(), before.expectedRagChunkIds());
        int expectedMinToolCalls = request.expectedMinToolCalls() == null ? before.expectedMinToolCalls() : Math.max(0, request.expectedMinToolCalls());
        int expectedTopK = request.expectedTopK() == null ? before.expectedTopK() : Math.max(1, Math.min(request.expectedTopK(), 20));
        String ragQuery = firstNonBlank(request.ragQuery(), before.ragQuery());
        String requestJson = requestJson(context, before, expectedTopK, ragQuery);
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
                    firstNonBlank(request.name(), "反馈样本：" + excerpt(before.sourceQuestion(), 48)),
                    before.endpoint(),
                    before.evalType(),
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
        EvalCaseCandidateResponse promoted = getCandidate(context.tenantId(), candidateId);
        writeCandidateAudit(context, promoted, "EVAL_CASE_PROMOTED", "评测候选已沉淀为正式评测用例", auditDetails(before, promoted, Map.of(
                "evalCaseId", firstNonBlank(promoted.evalCaseId(), ""),
                "enabled", Boolean.TRUE.equals(request.enabled())
        )));
        return promoted;
    }

    public FeedbackRagExperimentResponse createRagExperiment(String candidateId, String tenantId, String userId,
                                                             List<String> roles, boolean runNow) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        checkFeedbackMaintainer(context);
        EvalCaseCandidateResponse before = getCandidate(context.tenantId(), candidateId);
        if ("REJECTED".equals(before.reviewStatus())) {
            throw new IllegalArgumentException("已驳回的评测候选不能创建 RAG 实验");
        }
        if (before.expectedRagDocIds().isEmpty() && before.expectedRagChunkIds().isEmpty()) {
            throw new IllegalArgumentException("候选样本缺少期望引用，无法创建 RAG 实验");
        }
        String experimentId = before.ragExperimentId() == null || before.ragExperimentId().isBlank()
                ? "raglab-fb-" + before.candidateId().replace("cand-", "")
                : before.ragExperimentId();
        RagExperimentResponse experiment = ragExperimentService.upsert(new RagExperimentRequest(
                context.tenantId(),
                context.userId(),
                new ArrayList<>(context.roles()),
                experimentId,
                "反馈样本 RAG 对比：" + excerpt(before.sourceQuestion(), 42),
                "由反馈 " + before.feedbackId() + " 自动生成，用于诊断引用或检索质量问题。",
                firstNonBlank(before.ragQuery(), before.sourceQuestion()),
                before.expectedRagDocIds(),
                before.expectedRagChunkIds(),
                before.expectedTopK(),
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
        EvalCaseCandidateResponse updated = getCandidate(context.tenantId(), candidateId);
        writeCandidateAudit(context, updated, "RAG_EXPERIMENT_CREATED", "由候选创建 RAG 实验", auditDetails(before, updated, Map.of(
                "experimentId", experiment.experimentId(),
                "runNow", runNow,
                "runCount", runs.size()
        )));
        return new FeedbackRagExperimentResponse(updated, experiment, runs);
    }

    public FeedbackQualityMetricsResponse qualityMetrics(String tenantId, String userId, List<String> roles,
                                                         LocalDate fromDate, LocalDate toDate) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        checkFeedbackMaintainer(context);
        DateRange range = resolveDateRange(fromDate, toDate);
        Timestamp from = Timestamp.from(range.startInclusive());
        Timestamp to = Timestamp.from(range.endExclusive());
        long totalFeedback = count("""
                SELECT COUNT(*) FROM ai_agent_message_feedback
                WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
                """, context.tenantId(), from, to);
        long helpfulFeedback = count("""
                SELECT COUNT(*) FROM ai_agent_message_feedback
                WHERE tenant_id = ? AND rating = 'HELPFUL' AND created_at >= ? AND created_at < ?
                """, context.tenantId(), from, to);
        long notHelpfulFeedback = count("""
                SELECT COUNT(*) FROM ai_agent_message_feedback
                WHERE tenant_id = ? AND rating = 'NOT_HELPFUL' AND created_at >= ? AND created_at < ?
                """, context.tenantId(), from, to);
        long candidateCount = count("""
                SELECT COUNT(*) FROM ai_eval_case_candidate
                WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
                """, context.tenantId(), from, to);
        long approvedCandidates = count("""
                SELECT COUNT(*) FROM ai_eval_case_candidate
                WHERE tenant_id = ? AND review_status = 'APPROVED' AND created_at >= ? AND created_at < ?
                """, context.tenantId(), from, to);
        long rejectedCandidates = count("""
                SELECT COUNT(*) FROM ai_eval_case_candidate
                WHERE tenant_id = ? AND review_status = 'REJECTED' AND created_at >= ? AND created_at < ?
                """, context.tenantId(), from, to);
        long convertedEvalCases = count("""
                SELECT COUNT(*) FROM ai_eval_case_candidate
                WHERE tenant_id = ? AND eval_case_id IS NOT NULL AND created_at >= ? AND created_at < ?
                """, context.tenantId(), from, to);
        long ragExperimentCandidates = count("""
                SELECT COUNT(*) FROM ai_eval_case_candidate
                WHERE tenant_id = ? AND rag_experiment_id IS NOT NULL AND created_at >= ? AND created_at < ?
                """, context.tenantId(), from, to);
        List<MetricCount> byReason = groupCounts("""
                SELECT COALESCE(reason, 'UNKNOWN') AS name, COUNT(*) AS metric_count
                FROM ai_agent_message_feedback
                WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
                GROUP BY COALESCE(reason, 'UNKNOWN')
                ORDER BY metric_count DESC
                """, context.tenantId(), from, to);
        List<MetricCount> byReviewStatus = groupCounts("""
                SELECT review_status AS name, COUNT(*) AS metric_count
                FROM ai_eval_case_candidate
                WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
                GROUP BY review_status
                ORDER BY metric_count DESC
                """, context.tenantId(), from, to);
        List<MetricCount> ragExperimentStatus = groupCounts("""
                SELECT r.status AS name, COUNT(*) AS metric_count
                FROM ai_rag_experiment_run r
                JOIN ai_eval_case_candidate c ON r.tenant_id = c.tenant_id AND r.experiment_id = c.rag_experiment_id
                WHERE c.tenant_id = ? AND c.created_at >= ? AND c.created_at < ?
                GROUP BY r.status
                ORDER BY metric_count DESC
                """, context.tenantId(), from, to);
        List<MetricCount> evalRunStatus = groupCounts("""
                SELECT CASE WHEN r.passed = 1 THEN 'PASSED' ELSE 'FAILED' END AS name, COUNT(*) AS metric_count
                FROM ai_eval_case_result r
                JOIN ai_eval_case_candidate c ON r.case_id = c.eval_case_id
                WHERE c.tenant_id = ? AND c.created_at >= ? AND c.created_at < ?
                GROUP BY CASE WHEN r.passed = 1 THEN 'PASSED' ELSE 'FAILED' END
                ORDER BY metric_count DESC
                """, context.tenantId(), from, to);
        List<MetricCount> byTag = tagCounts(context.tenantId(), from, to);
        List<DailyQualityTrend> dailyTrends = dailyTrends(context.tenantId(), range.fromDate(), range.toDate(), from, to);
        long ragRunTotal = sumCounts(ragExperimentStatus);
        long ragRunPassed = countByName(ragExperimentStatus, "PASSED");
        long evalResultTotal = sumCounts(evalRunStatus);
        long evalResultPassed = countByName(evalRunStatus, "PASSED");
        return new FeedbackQualityMetricsResponse(
                range.fromDate(),
                range.toDate(),
                totalFeedback,
                helpfulFeedback,
                notHelpfulFeedback,
                rate(notHelpfulFeedback, totalFeedback),
                candidateCount,
                approvedCandidates,
                rejectedCandidates,
                convertedEvalCases,
                ragExperimentCandidates,
                rate(candidateCount, notHelpfulFeedback),
                rate(approvedCandidates, candidateCount),
                rate(ragRunPassed, ragRunTotal),
                rate(evalResultPassed, evalResultTotal),
                byReason,
                byTag,
                byReviewStatus,
                ragExperimentStatus,
                evalRunStatus,
                dailyTrends
        );
    }

    public List<FeedbackCandidateAuditResponse> listCandidateAudits(String tenantId, String userId, List<String> roles,
                                                                    String candidateId, String actionType, int limit) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        checkFeedbackMaintainer(context);
        StringBuilder sql = new StringBuilder("""
                SELECT *
                FROM ai_eval_case_candidate_audit
                WHERE tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(context.tenantId());
        if (candidateId != null && !candidateId.isBlank()) {
            sql.append(" AND candidate_id = ?");
            args.add(candidateId.trim());
        }
        if (actionType != null && !actionType.isBlank()) {
            sql.append(" AND action_type = ?");
            args.add(actionType.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 100)));
        return jdbcTemplate.query(sql.toString(), this::mapCandidateAudit, args.toArray());
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
                splitLines(rs.getString("feedback_tags")),
                rs.getString("annotation_note"),
                rs.getString("review_status"),
                rs.getString("reviewer_id"),
                rs.getString("review_comment"),
                instantOrNull(rs.getTimestamp("reviewed_at")),
                rs.getString("annotated_by"),
                instantOrNull(rs.getTimestamp("annotated_at")),
                rs.getString("eval_case_id"),
                rs.getString("rag_experiment_id"),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private FeedbackCandidateAuditResponse mapCandidateAudit(ResultSet rs, int rowNum) throws SQLException {
        return new FeedbackCandidateAuditResponse(
                rs.getString("audit_id"),
                rs.getString("tenant_id"),
                rs.getString("candidate_id"),
                rs.getString("feedback_id"),
                rs.getString("action_type"),
                rs.getString("actor_id"),
                rs.getString("review_status"),
                rs.getString("summary"),
                rs.getString("detail_json"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private void writeCandidateAudit(AgentUserContext context, EvalCaseCandidateResponse candidate,
                                     String actionType, String summary, Map<String, ?> details) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        INSERT INTO ai_eval_case_candidate_audit
                        (tenant_id, audit_id, candidate_id, feedback_id, action_type, actor_id,
                         review_status, summary, detail_json, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                context.tenantId(),
                "audit-" + UUID.randomUUID().toString().substring(0, 12),
                candidate.candidateId(),
                candidate.feedbackId(),
                actionType,
                context.userId(),
                candidate.reviewStatus(),
                summary,
                toJson(details),
                Timestamp.from(now));
    }

    private Map<String, Object> auditDetails(EvalCaseCandidateResponse before, EvalCaseCandidateResponse after,
                                             Map<String, ?> extra) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.putAll(extra);
        details.put("changedFields", changedFields(before, after));
        details.put("before", candidateSnapshot(before));
        details.put("after", candidateSnapshot(after));
        return details;
    }

    private Map<String, Object> changedFields(EvalCaseCandidateResponse before, EvalCaseCandidateResponse after) {
        Map<String, Object> changes = new java.util.LinkedHashMap<>();
        putChange(changes, "endpoint", before.endpoint(), after.endpoint());
        putChange(changes, "evalType", before.evalType(), after.evalType());
        putChange(changes, "expectedContains", before.expectedContains(), after.expectedContains());
        putChange(changes, "expectedCitations", before.expectedCitations(), after.expectedCitations());
        putChange(changes, "expectedRagDocIds", before.expectedRagDocIds(), after.expectedRagDocIds());
        putChange(changes, "expectedRagChunkIds", before.expectedRagChunkIds(), after.expectedRagChunkIds());
        putChange(changes, "expectedMinToolCalls", before.expectedMinToolCalls(), after.expectedMinToolCalls());
        putChange(changes, "expectedTopK", before.expectedTopK(), after.expectedTopK());
        putChange(changes, "ragQuery", before.ragQuery(), after.ragQuery());
        putChange(changes, "status", before.status(), after.status());
        putChange(changes, "feedbackTags", before.feedbackTags(), after.feedbackTags());
        putChange(changes, "annotationNote", before.annotationNote(), after.annotationNote());
        putChange(changes, "reviewStatus", before.reviewStatus(), after.reviewStatus());
        putChange(changes, "reviewerId", before.reviewerId(), after.reviewerId());
        putChange(changes, "reviewComment", before.reviewComment(), after.reviewComment());
        putChange(changes, "evalCaseId", before.evalCaseId(), after.evalCaseId());
        putChange(changes, "ragExperimentId", before.ragExperimentId(), after.ragExperimentId());
        return changes;
    }

    private void putChange(Map<String, Object> changes, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.put(field, Map.of("before", before == null ? "" : before, "after", after == null ? "" : after));
        }
    }

    private Map<String, Object> candidateSnapshot(EvalCaseCandidateResponse candidate) {
        Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("candidateId", candidate.candidateId());
        snapshot.put("feedbackId", candidate.feedbackId());
        snapshot.put("endpoint", candidate.endpoint());
        snapshot.put("evalType", candidate.evalType());
        snapshot.put("expectedContains", candidate.expectedContains());
        snapshot.put("expectedCitations", candidate.expectedCitations());
        snapshot.put("expectedRagDocIds", candidate.expectedRagDocIds());
        snapshot.put("expectedRagChunkIds", candidate.expectedRagChunkIds());
        snapshot.put("expectedTopK", candidate.expectedTopK());
        snapshot.put("ragQuery", firstNonBlank(candidate.ragQuery(), ""));
        snapshot.put("status", candidate.status());
        snapshot.put("feedbackTags", candidate.feedbackTags());
        snapshot.put("annotationNote", firstNonBlank(candidate.annotationNote(), ""));
        snapshot.put("reviewStatus", firstNonBlank(candidate.reviewStatus(), ""));
        snapshot.put("reviewerId", firstNonBlank(candidate.reviewerId(), ""));
        snapshot.put("reviewComment", firstNonBlank(candidate.reviewComment(), ""));
        snapshot.put("evalCaseId", firstNonBlank(candidate.evalCaseId(), ""));
        snapshot.put("ragExperimentId", firstNonBlank(candidate.ragExperimentId(), ""));
        return snapshot;
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

    private String normalizeReviewStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("reviewStatus 不能为空");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("UNREVIEWED", "REVIEWING", "APPROVED", "REJECTED").contains(normalized)) {
            throw new IllegalArgumentException("reviewStatus 只能是 UNREVIEWED、REVIEWING、APPROVED 或 REJECTED");
        }
        return normalized;
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

    private List<String> defaultFeedbackTags(String reason) {
        if ("CITATION_WEAK".equalsIgnoreCase(reason)) {
            return List.of("RAG_QUALITY");
        }
        if ("BUSINESS_DATA_MISSING".equalsIgnoreCase(reason)) {
            return List.of("TOOL_OR_DATA");
        }
        if ("ACTION_RISK".equalsIgnoreCase(reason)) {
            return List.of("ACTION_SAFETY");
        }
        return List.of("ANSWER_QUALITY");
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private List<MetricCount> groupCounts(String sql, Object... args) {
        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new MetricCount(rs.getString("name"), rs.getLong("metric_count")), args);
    }

    private List<MetricCount> tagCounts(String tenantId, Timestamp from, Timestamp to) {
        List<String> rows = jdbcTemplate.query("""
                        SELECT feedback_tags
                        FROM ai_eval_case_candidate
                        WHERE tenant_id = ? AND feedback_tags IS NOT NULL
                          AND created_at >= ? AND created_at < ?
                        """,
                (rs, rowNum) -> rs.getString("feedback_tags"),
                tenantId,
                from,
                to);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String row : rows) {
            for (String tag : splitLines(row)) {
                counts.merge(tag, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
                .map(entry -> new MetricCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<DailyQualityTrend> dailyTrends(String tenantId, LocalDate fromDate, LocalDate toDate,
                                                Timestamp from, Timestamp to) {
        Map<LocalDate, TrendBucket> buckets = new LinkedHashMap<>();
        LocalDate current = fromDate;
        while (!current.isAfter(toDate)) {
            buckets.put(current, new TrendBucket());
            current = current.plusDays(1);
        }

        jdbcTemplate.query("""
                        SELECT rating, created_at
                        FROM ai_agent_message_feedback
                        WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
                        """,
                rs -> {
                    LocalDate day = localDate(rs.getTimestamp("created_at"));
                    TrendBucket bucket = buckets.get(day);
                    if (bucket != null) {
                        bucket.totalFeedback++;
                        if ("NOT_HELPFUL".equals(rs.getString("rating"))) {
                            bucket.notHelpfulFeedback++;
                        }
                    }
                },
                tenantId,
                from,
                to);

        jdbcTemplate.query("""
                        SELECT review_status, rag_experiment_id, created_at
                        FROM ai_eval_case_candidate
                        WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
                        """,
                rs -> {
                    LocalDate day = localDate(rs.getTimestamp("created_at"));
                    TrendBucket bucket = buckets.get(day);
                    if (bucket != null) {
                        bucket.candidateCount++;
                        if ("APPROVED".equals(rs.getString("review_status"))) {
                            bucket.approvedCandidates++;
                        }
                        String ragExperimentId = rs.getString("rag_experiment_id");
                        if (ragExperimentId != null && !ragExperimentId.isBlank()) {
                            bucket.ragExperimentCandidates++;
                        }
                    }
                },
                tenantId,
                from,
                to);

        return buckets.entrySet().stream()
                .map(entry -> {
                    TrendBucket bucket = entry.getValue();
                    return new DailyQualityTrend(
                            entry.getKey(),
                            bucket.totalFeedback,
                            bucket.notHelpfulFeedback,
                            bucket.candidateCount,
                            bucket.approvedCandidates,
                            bucket.ragExperimentCandidates,
                            rate(bucket.notHelpfulFeedback, bucket.totalFeedback),
                            rate(bucket.candidateCount, bucket.notHelpfulFeedback),
                            rate(bucket.approvedCandidates, bucket.candidateCount)
                    );
                })
                .toList();
    }

    private DateRange resolveDateRange(LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate toDate = requestedTo == null ? LocalDate.now(SYSTEM_ZONE) : requestedTo;
        LocalDate fromDate = requestedFrom == null ? toDate.minusDays(29) : requestedFrom;
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("from 不能晚于 to");
        }
        if (fromDate.plusDays(180).isBefore(toDate)) {
            throw new IllegalArgumentException("质量趋势时间范围最多支持 180 天");
        }
        return new DateRange(
                fromDate,
                toDate,
                fromDate.atStartOfDay(SYSTEM_ZONE).toInstant(),
                toDate.plusDays(1).atStartOfDay(SYSTEM_ZONE).toInstant()
        );
    }

    private LocalDate localDate(Timestamp timestamp) {
        return timestamp.toInstant().atZone(SYSTEM_ZONE).toLocalDate();
    }

    private String reviewAuditType(String reviewStatus) {
        if ("APPROVED".equals(reviewStatus)) {
            return "CANDIDATE_APPROVED";
        }
        if ("REJECTED".equals(reviewStatus)) {
            return "CANDIDATE_REJECTED";
        }
        return "CANDIDATE_REVIEW_UPDATED";
    }

    private String toJson(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private long sumCounts(List<MetricCount> rows) {
        return rows.stream().mapToLong(MetricCount::count).sum();
    }

    private long countByName(List<MetricCount> rows, String name) {
        return rows.stream()
                .filter(row -> name.equals(row.name()))
                .mapToLong(MetricCount::count)
                .sum();
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round((numerator * 1.0 / denominator) * 10000.0) / 10000.0;
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

    private Instant instantOrNull(Timestamp value) {
        return value == null ? null : value.toInstant();
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

    private record DateRange(LocalDate fromDate, LocalDate toDate, Instant startInclusive, Instant endExclusive) {
    }

    private static class TrendBucket {
        private long totalFeedback;
        private long notHelpfulFeedback;
        private long candidateCount;
        private long approvedCandidates;
        private long ragExperimentCandidates;
    }
}
