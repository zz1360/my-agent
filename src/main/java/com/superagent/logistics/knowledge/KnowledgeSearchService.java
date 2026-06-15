package com.superagent.logistics.knowledge;

import com.superagent.logistics.config.RetrievalProperties;
import com.superagent.logistics.security.AgentPermissionService;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class KnowledgeSearchService {

    private static final List<String> DOMAIN_TERMS = List.of(
            "延误", "赔付", "补偿", "冷链", "温控", "超温", "派送失败", "工单", "投诉",
            "SLA", "时效", "签收", "轨迹", "VIP", "风险", "异常", "破损", "丢件",
            "仓配", "出库", "客服", "升级", "责任", "合同"
    );

    private final JdbcTemplate jdbcTemplate;
    private final AgentPermissionService permissionService;
    private final PgVectorKnowledgeStore vectorKnowledgeStore;
    private final KnowledgeReranker knowledgeReranker;
    private final RetrievalProperties retrievalProperties;

    public KnowledgeSearchService(JdbcTemplate jdbcTemplate,
                                  AgentPermissionService permissionService,
                                  PgVectorKnowledgeStore vectorKnowledgeStore,
                                  KnowledgeReranker knowledgeReranker,
                                  RetrievalProperties retrievalProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionService = permissionService;
        this.vectorKnowledgeStore = vectorKnowledgeStore;
        this.knowledgeReranker = knowledgeReranker;
        this.retrievalProperties = retrievalProperties;
    }

    public List<KnowledgeSearchResult> search(AgentUserContext context, String query, int topK) {
        return searchWithDiagnostics(context, query, topK, retrievalProperties.defaultOptions()).results();
    }

    public List<KnowledgeSearchResult> search(AgentUserContext context, String query, int topK,
                                              KnowledgeSearchOptions options) {
        return searchWithDiagnostics(context, query, topK, options).results();
    }

    public KnowledgeSearchDiagnostics searchWithDiagnostics(AgentUserContext context, String query, int topK) {
        return searchWithDiagnostics(context, query, topK, retrievalProperties.defaultOptions());
    }

    public KnowledgeSearchDiagnostics searchWithDiagnostics(AgentUserContext context, String query, int topK,
                                                           KnowledgeSearchOptions options) {
        int resultLimit = Math.max(1, Math.min(topK, 8));
        KnowledgeSearchOptions effectiveOptions = options == null ? KnowledgeSearchOptions.defaults() : options;
        String retrievalMode = modeForOptions(effectiveOptions);
        boolean vectorReady = vectorKnowledgeStore.isReady();
        List<KnowledgeChunk> activeChunks = findActiveChunks(context.tenantId()).stream()
                .filter(chunk -> permissionService.canReadKnowledge(context, chunk.aclRoles()))
                .toList();
        Map<String, KnowledgeChunk> activeByKey = new LinkedHashMap<>();
        for (KnowledgeChunk chunk : activeChunks) {
            activeByKey.put(chunkKey(chunk), chunk);
        }
        Set<String> terms = extractTerms(query);
        Map<String, Candidate> candidates = new LinkedHashMap<>();

        if (effectiveOptions.useVector() && vectorReady) {
            for (KnowledgeSearchResult vectorResult : vectorKnowledgeStore.search(context.tenantId(), query, resultLimit * 4)) {
                KnowledgeChunk activeChunk = activeByKey.get(chunkKey(vectorResult.chunk()));
                if (activeChunk != null) {
                    Candidate candidate = candidates.computeIfAbsent(chunkKey(activeChunk), key -> new Candidate(activeChunk));
                    candidate.vectorScore = Math.max(candidate.vectorScore, Math.max(0, vectorResult.score()));
                }
            }
        }

        if (effectiveOptions.useKeyword()) {
            for (KnowledgeChunk chunk : activeChunks) {
                double keywordScore = keywordScore(chunk, terms, query);
                if (keywordScore > 0) {
                    Candidate candidate = candidates.computeIfAbsent(chunkKey(chunk), key -> new Candidate(chunk));
                    candidate.keywordScore = Math.max(candidate.keywordScore, keywordScore);
                }
            }
        }
        if (candidates.isEmpty()) {
            return diagnostics(query, retrievalMode, resultLimit, vectorReady, effectiveOptions,
                    activeChunks.size(), 0, 0, List.of(), activeChunks);
        }
        double maxVectorScore = candidates.values().stream().mapToDouble(candidate -> candidate.vectorScore).max().orElse(0);
        double maxKeywordScore = candidates.values().stream().mapToDouble(candidate -> candidate.keywordScore).max().orElse(0);
        List<KnowledgeRerankCandidate> rerankCandidates = candidates.values().stream()
                .map(candidate -> new KnowledgeRerankCandidate(candidate.chunk, normalizedScore(candidate.vectorScore, maxVectorScore),
                        normalizedScore(candidate.keywordScore, maxKeywordScore),
                        ruleScore(candidate, terms, query, maxVectorScore, maxKeywordScore)))
                .filter(candidate -> candidate.ruleScore() > 0)
                .sorted(Comparator.comparingDouble(KnowledgeRerankCandidate::ruleScore).reversed())
                .toList();
        List<KnowledgeSearchResult> results;
        if (!effectiveOptions.useReranker()) {
            results = rerankCandidates.stream()
                    .limit(resultLimit)
                    .map(candidate -> new KnowledgeSearchResult(candidate.chunk(), candidate.ruleScore(),
                            candidate.vectorScore(), candidate.keywordScore(), candidate.ruleScore(), null, "rule-only"))
                    .toList();
            return diagnostics(query, retrievalMode, resultLimit, vectorReady, effectiveOptions,
                    activeChunks.size(), candidates.size(), rerankCandidates.size(), results, activeChunks);
        }
        results = knowledgeReranker.rerank(query, rerankCandidates, resultLimit);
        return diagnostics(query, retrievalMode, resultLimit, vectorReady, effectiveOptions,
                activeChunks.size(), candidates.size(), rerankCandidates.size(), results, activeChunks);
    }

    private List<KnowledgeChunk> findActiveChunks(String tenantId) {
        return jdbcTemplate.query("""
                SELECT c.doc_id, c.chunk_id, c.title_path, c.content, c.metadata, c.acl_roles
                FROM ai_knowledge_chunk c
                JOIN ai_knowledge_document d
                  ON d.tenant_id = c.tenant_id AND d.doc_id = c.doc_id
                WHERE c.tenant_id = ? AND d.status = 'ACTIVE'
                  AND (d.effective_from IS NULL OR d.effective_from <= CURRENT_DATE)
                  AND (d.effective_to IS NULL OR d.effective_to >= CURRENT_DATE)
                """, this::mapChunk, tenantId);
    }

    private String chunkKey(KnowledgeChunk chunk) {
        return chunk.docId() + "/" + chunk.chunkId();
    }

    private KnowledgeSearchDiagnostics diagnostics(String query,
                                                   String retrievalMode,
                                                   int requestedTopK,
                                                   boolean vectorReady,
                                                   KnowledgeSearchOptions options,
                                                   int activeChunkCount,
                                                   int candidateCount,
                                                   int rerankCandidateCount,
                                                   List<KnowledgeSearchResult> results,
                                                   List<KnowledgeChunk> activeChunks) {
        List<KnowledgeSearchResult> safeResults = results == null ? List.of() : results;
        return new KnowledgeSearchDiagnostics(
                query,
                retrievalMode,
                requestedTopK,
                vectorReady,
                options.useVector() && vectorReady,
                options.useKeyword(),
                options.useReranker(),
                activeChunkCount,
                candidateCount,
                rerankCandidateCount,
                safeResults.size(),
                knowledgeVersion(safeResults, activeChunks),
                safeResults
        );
    }

    private String modeForOptions(KnowledgeSearchOptions options) {
        if (options.useVector() && options.useKeyword() && options.useReranker()) {
            return "HYBRID_RERANKER";
        }
        if (options.useVector() && options.useKeyword()) {
            return "HYBRID_RULE";
        }
        if (options.useVector()) {
            return "VECTOR_ONLY";
        }
        return "KEYWORD_ONLY";
    }

    private String knowledgeVersion(List<KnowledgeSearchResult> results, List<KnowledgeChunk> activeChunks) {
        List<KnowledgeChunk> chunks = results == null || results.isEmpty()
                ? activeChunks
                : results.stream().map(KnowledgeSearchResult::chunk).toList();
        String version = chunks.stream()
                .map(chunk -> chunk.docId() + "@" + metadataValue(chunk.metadata(), "version", "unknown"))
                .distinct()
                .limit(12)
                .collect(java.util.stream.Collectors.joining(","));
        return version.isBlank() ? "none" : version;
    }

    private String metadataValue(String metadata, String key, String fallback) {
        if (metadata == null || metadata.isBlank()) {
            return fallback;
        }
        String prefix = key + "=";
        for (String item : metadata.split(";")) {
            String trimmed = item.trim();
            if (trimmed.startsWith(prefix)) {
                String value = trimmed.substring(prefix.length()).trim();
                return value.isBlank() ? fallback : value;
            }
        }
        return fallback;
    }

    private Set<String> extractTerms(String query) {
        Set<String> terms = new LinkedHashSet<>();
        if (query == null) {
            return terms;
        }
        String normalized = query.toUpperCase(Locale.ROOT);
        for (String term : DOMAIN_TERMS) {
            if (normalized.contains(term.toUpperCase(Locale.ROOT))) {
                terms.add(term.toUpperCase(Locale.ROOT));
            }
        }
        for (String token : normalized.replaceAll("[^A-Z0-9\\u4E00-\\u9FA5]+", " ").split("\\s+")) {
            if (token.length() >= 2) {
                terms.add(token);
            }
        }
        return terms;
    }

    private double keywordScore(KnowledgeChunk chunk, Set<String> terms, String originalQuery) {
        if (terms.isEmpty()) {
            return 0;
        }
        String title = chunk.title().toUpperCase(Locale.ROOT);
        String content = chunk.content().toUpperCase(Locale.ROOT);
        double score = 0;
        for (String term : terms) {
            if (title.contains(term)) {
                score += 3.2;
            }
            if (content.contains(term)) {
                score += 1.6;
            }
        }
        if (originalQuery != null && originalQuery.contains("为什么") && title.contains("风险")) {
            score += 1.2;
        }
        if (originalQuery != null && originalQuery.contains("怎么处理") && title.contains("SOP")) {
            score += 1.4;
        }
        if (originalQuery != null && originalQuery.contains("是否") && title.contains("政策")) {
            score += 1.0;
        }
        return score;
    }

    private double ruleScore(Candidate candidate, Set<String> terms, String originalQuery,
                             double maxVectorScore, double maxKeywordScore) {
        double vector = normalizedScore(candidate.vectorScore, maxVectorScore);
        double keyword = normalizedScore(candidate.keywordScore, maxKeywordScore);
        double intent = intentBoost(candidate.chunk, originalQuery);
        double coverage = termCoverage(candidate.chunk, terms);
        return vector * 0.56 + keyword * 0.30 + intent * 0.08 + coverage * 0.06;
    }

    private double normalizedScore(double score, double maxScore) {
        return maxScore <= 0 ? 0 : score / maxScore;
    }

    private double intentBoost(KnowledgeChunk chunk, String originalQuery) {
        if (originalQuery == null || originalQuery.isBlank()) {
            return 0;
        }
        String query = originalQuery.toUpperCase(Locale.ROOT);
        String title = chunk.title().toUpperCase(Locale.ROOT);
        String content = chunk.content().toUpperCase(Locale.ROOT);
        double boost = 0;
        if ((query.contains("赔") || query.contains("补偿")) && (title.contains("赔付") || content.contains("赔付"))) {
            boost += 0.45;
        }
        if ((query.contains("怎么处理") || query.contains("怎么办")) && (title.contains("SOP") || content.contains("处理"))) {
            boost += 0.35;
        }
        if ((query.contains("冷链") || query.contains("温度") || query.contains("超温"))
                && (title.contains("冷链") || content.contains("温控"))) {
            boost += 0.45;
        }
        if ((query.contains("投诉") || query.contains("风险")) && (title.contains("风险") || content.contains("投诉"))) {
            boost += 0.30;
        }
        return Math.min(1.0, boost);
    }

    private double termCoverage(KnowledgeChunk chunk, Set<String> terms) {
        if (terms.isEmpty()) {
            return 0;
        }
        String haystack = (chunk.title() + "\n" + chunk.content()).toUpperCase(Locale.ROOT);
        long matches = terms.stream().filter(haystack::contains).count();
        return Math.min(1.0, matches / (double) terms.size());
    }

    private KnowledgeChunk mapChunk(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgeChunk(
                rs.getString("doc_id"),
                rs.getString("chunk_id"),
                rs.getString("title_path"),
                rs.getString("content"),
                rs.getString("metadata"),
                rs.getString("acl_roles")
        );
    }

    public List<String> demoQuestions() {
        List<String> questions = new ArrayList<>();
        questions.add("客户 C001 最近 30 天为什么投诉量上升？相关处理制度是什么？");
        questions.add("运单 WB202606010023 现在是什么状态？轨迹给我看一下。");
        questions.add("运单 WB202606010023 是否可能满足延误赔付条件？");
        questions.add("冷链运输超温后应该怎么处理？");
        questions.add("帮我生成客户 C001 本周服务诊断摘要。");
        questions.add("华东区高风险客户有哪些？");
        return questions;
    }

    public String defaultMode() {
        return retrievalProperties.normalizedDefaultMode();
    }

    private static class Candidate {
        private final KnowledgeChunk chunk;
        private double vectorScore;
        private double keywordScore;

        private Candidate(KnowledgeChunk chunk) {
            this.chunk = chunk;
        }
    }
}
