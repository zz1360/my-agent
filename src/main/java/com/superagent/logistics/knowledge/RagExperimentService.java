package com.superagent.logistics.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.logistics.api.dto.RagExperimentRequest;
import com.superagent.logistics.api.dto.RagExperimentResponse;
import com.superagent.logistics.api.dto.RagExperimentRunResponse;
import com.superagent.logistics.security.AgentPermissionService;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Order(2)
public class RagExperimentService implements ApplicationRunner {

    private static final List<String> DEFAULT_MODES = List.of("KEYWORD_ONLY", "HYBRID_RULE", "HYBRID_RERANKER");

    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeSearchService searchService;
    private final AgentPermissionService permissionService;
    private final ObjectMapper objectMapper;

    public RagExperimentService(JdbcTemplate jdbcTemplate,
                                KnowledgeSearchService searchService,
                                AgentPermissionService permissionService,
                                ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.searchService = searchService;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM ai_rag_experiment
                WHERE tenant_id = ?
                """, Integer.class, "T001");
        if (count != null && count > 0) {
            return;
        }
        Instant now = Instant.now();
        insertExperiment("T001", "raglab-delay-compensation", "延误赔付政策召回对比",
                "比较关键词、混合规则和 reranker 对延误赔付制度的召回排序。",
                "u-eval", "CUSTOMER_SERVICE",
                "VIP 客户晚到补偿和延误赔付怎么判断？",
                "policy-delay-v3", "policy-delay-v3-chunk-001", 5, DEFAULT_MODES, now);
        insertExperiment("T001", "raglab-cold-chain-treatment", "冷链超温 SOP 召回对比",
                "比较冷链超温问题在不同检索策略下是否能排到温控规范。",
                "u-eval", "CUSTOMER_SERVICE",
                "冷链运输温度超过 10C 后客服应该怎么处理？",
                "policy-cold-chain-v2", "policy-cold-chain-v2-chunk-001", 5, DEFAULT_MODES, now);
    }

    public List<RagExperimentResponse> list(String tenantId, String userId, List<String> roles, boolean enabledOnly) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        String sql = """
                SELECT *
                FROM ai_rag_experiment
                WHERE tenant_id = ?
                %s
                ORDER BY updated_at DESC
                """.formatted(enabledOnly ? "AND enabled = 1" : "");
        return jdbcTemplate.query(sql, this::mapExperiment, context.tenantId());
    }

    public RagExperimentResponse upsert(RagExperimentRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        permissionService.checkBusinessReadable(context);
        String experimentId = resolveExperimentId(request.experimentId());
        List<String> modes = resolveModes(request.modes());
        List<String> expectedDocIds = normalizeList(request.expectedDocIds());
        List<String> expectedChunkIds = normalizeList(request.expectedChunkIds());
        int topK = request.topK() == null ? 5 : Math.max(1, Math.min(request.topK(), 20));
        Instant now = Instant.now();

        jdbcTemplate.update("""
                DELETE FROM ai_rag_experiment
                WHERE tenant_id = ? AND experiment_id = ?
                """, context.tenantId(), experimentId);
        jdbcTemplate.update("""
                INSERT INTO ai_rag_experiment
                (tenant_id, experiment_id, name, description, user_id, roles, query,
                 expected_doc_ids, expected_chunk_ids, top_k, modes, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, context.tenantId(), experimentId, request.name().trim(), blankToNull(request.description()),
                context.userId(), joinLines(new ArrayList<>(context.roles())), request.query().trim(), joinLines(expectedDocIds),
                joinLines(expectedChunkIds), topK, joinLines(modes), request.enabled() == null || request.enabled(),
                Timestamp.from(now), Timestamp.from(now));
        return get(context.tenantId(), context.userId(), new ArrayList<>(context.roles()), experimentId);
    }

    public RagExperimentResponse get(String tenantId, String userId, List<String> roles, String experimentId) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        List<RagExperimentResponse> experiments = jdbcTemplate.query("""
                SELECT *
                FROM ai_rag_experiment
                WHERE tenant_id = ? AND experiment_id = ?
                """, this::mapExperiment, context.tenantId(), experimentId);
        return experiments.stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到 RAG 实验：" + experimentId));
    }

    public List<RagExperimentRunResponse> runExperiment(String tenantId, String userId, List<String> roles,
                                                        String experimentId, List<String> modeOverride) {
        RagExperimentResponse experiment = get(tenantId, userId, roles, experimentId);
        AgentUserContext context = AgentUserContext.from(experiment.tenantId(), experiment.userId(), experiment.roles());
        List<String> modes = modeOverride == null || modeOverride.isEmpty() ? experiment.modes() : resolveModes(modeOverride);
        List<RagExperimentRunResponse> responses = new ArrayList<>();
        for (String mode : modes) {
            responses.add(runMode(context, experiment, mode));
        }
        return responses;
    }

    public List<RagExperimentRunResponse> listRuns(String tenantId, String userId, List<String> roles,
                                                   String experimentId, int limit) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        return jdbcTemplate.query("""
                SELECT *
                FROM ai_rag_experiment_run
                WHERE tenant_id = ? AND experiment_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """, this::mapRun, context.tenantId(), experimentId, Math.max(1, Math.min(limit, 100)));
    }

    private RagExperimentRunResponse runMode(AgentUserContext context, RagExperimentResponse experiment, String mode) {
        long start = System.nanoTime();
        List<KnowledgeSearchResult> results = searchService.search(context, experiment.query(), experiment.topK(), options(mode));
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        RagMetrics metrics = calculateMetrics(results, experiment.expectedDocIds(), experiment.expectedChunkIds(), experiment.topK());
        String status = metrics.expectedTotal() == 0 || metrics.hitCount() >= metrics.expectedTotal() ? "PASSED" : "FAILED";
        String metricsJson = metricsJson(experiment, mode, results, metrics, latencyMs);
        String runId = "rag-run-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        List<String> topDocIds = distinct(results.stream().map(result -> result.chunk().docId()).toList());
        List<String> topChunkIds = distinct(results.stream().map(result -> result.chunk().chunkId()).toList());
        jdbcTemplate.update("""
                INSERT INTO ai_rag_experiment_run
                (tenant_id, run_id, experiment_id, mode, status, recall_at_k, precision_at_k,
                 mrr, ndcg, hit_count, expected_total, latency_ms, top_doc_ids, top_chunk_ids,
                 metrics_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, experiment.tenantId(), runId, experiment.experimentId(), mode, status,
                metrics.recallAtK(), metrics.precisionAtK(), metrics.mrr(), metrics.ndcg(),
                metrics.hitCount(), metrics.expectedTotal(), latencyMs, joinLines(topDocIds),
                joinLines(topChunkIds), metricsJson, Timestamp.from(now));
        return getRun(runId);
    }

    private RagExperimentRunResponse getRun(String runId) {
        List<RagExperimentRunResponse> runs = jdbcTemplate.query("""
                SELECT *
                FROM ai_rag_experiment_run
                WHERE run_id = ?
                """, this::mapRun, runId);
        return runs.stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到 RAG 实验运行：" + runId));
    }

    private KnowledgeSearchOptions options(String mode) {
        return switch (mode) {
            case "KEYWORD_ONLY" -> KnowledgeSearchOptions.keywordOnly();
            case "VECTOR_ONLY" -> KnowledgeSearchOptions.vectorOnly();
            case "HYBRID_RULE" -> KnowledgeSearchOptions.hybridWithoutReranker();
            case "HYBRID_RERANKER" -> KnowledgeSearchOptions.hybridWithReranker();
            default -> throw new IllegalArgumentException("不支持的 RAG 实验模式：" + mode);
        };
    }

    private RagMetrics calculateMetrics(List<KnowledgeSearchResult> results, List<String> expectedDocs,
                                        List<String> expectedChunks, int topK) {
        Set<String> expectedDocSet = new LinkedHashSet<>(expectedDocs);
        Set<String> expectedChunkSet = new LinkedHashSet<>(expectedChunks);
        int expectedTotal = expectedDocSet.size() + expectedChunkSet.size();
        int hitCount = 0;
        List<String> topDocs = results.stream().limit(topK).map(result -> result.chunk().docId()).toList();
        List<String> topChunks = results.stream().limit(topK).map(result -> result.chunk().chunkId()).toList();
        for (String expectedDoc : expectedDocSet) {
            if (topDocs.contains(expectedDoc)) {
                hitCount++;
            }
        }
        for (String expectedChunk : expectedChunkSet) {
            if (topChunks.contains(expectedChunk)) {
                hitCount++;
            }
        }
        double recall = expectedTotal == 0 ? 1.0 : hitCount / (double) expectedTotal;
        double precision = results.isEmpty() ? 0.0 : hitCount / (double) Math.min(topK, results.size());
        double mrr = 0.0;
        double dcg = 0.0;
        for (int i = 0; i < Math.min(topK, results.size()); i++) {
            KnowledgeSearchResult result = results.get(i);
            boolean relevant = expectedDocSet.contains(result.chunk().docId()) || expectedChunkSet.contains(result.chunk().chunkId());
            if (relevant && mrr == 0.0) {
                mrr = 1.0 / (i + 1);
            }
            if (relevant) {
                dcg += 1.0 / (Math.log(i + 2) / Math.log(2));
            }
        }
        double ideal = 0.0;
        for (int i = 0; i < Math.min(expectedTotal, topK); i++) {
            ideal += 1.0 / (Math.log(i + 2) / Math.log(2));
        }
        double ndcg = ideal == 0.0 ? 0.0 : dcg / ideal;
        return new RagMetrics(decimal(recall), decimal(precision), decimal(mrr), decimal(ndcg), hitCount, expectedTotal);
    }

    private String metricsJson(RagExperimentResponse experiment, String mode, List<KnowledgeSearchResult> results,
                               RagMetrics metrics, long latencyMs) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("experimentId", experiment.experimentId());
            payload.put("mode", mode);
            payload.put("query", experiment.query());
            payload.put("topK", experiment.topK());
            payload.put("latencyMs", latencyMs);
            payload.put("recallAtK", metrics.recallAtK());
            payload.put("precisionAtK", metrics.precisionAtK());
            payload.put("mrr", metrics.mrr());
            payload.put("ndcg", metrics.ndcg());
            payload.put("expectedTotal", metrics.expectedTotal());
            payload.put("hitCount", metrics.hitCount());
            payload.put("scores", results.stream().map(result -> Map.of(
                    "docId", result.chunk().docId(),
                    "chunkId", result.chunk().chunkId(),
                    "score", result.score(),
                    "vectorScore", result.vectorScore(),
                    "keywordScore", result.keywordScore(),
                    "ruleScore", result.ruleScore(),
                    "rerankerScore", result.rerankerScore() == null ? "" : result.rerankerScore(),
                    "rerankerProvider", result.rerankerProvider()
            )).toList());
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("RAG 实验指标序列化失败：" + ex.getMessage(), ex);
        }
    }

    private void insertExperiment(String tenantId, String experimentId, String name, String description,
                                  String userId, String roles, String query, String expectedDocIds,
                                  String expectedChunkIds, int topK, List<String> modes, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO ai_rag_experiment
                (tenant_id, experiment_id, name, description, user_id, roles, query,
                 expected_doc_ids, expected_chunk_ids, top_k, modes, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, tenantId, experimentId, name, description, userId, roles, query, expectedDocIds,
                expectedChunkIds, topK, joinLines(modes), true, Timestamp.from(now), Timestamp.from(now));
    }

    private RagExperimentResponse mapExperiment(ResultSet rs, int rowNum) throws SQLException {
        return new RagExperimentResponse(
                rs.getString("tenant_id"),
                rs.getString("experiment_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("user_id"),
                splitLines(rs.getString("roles")),
                rs.getString("query"),
                splitLines(rs.getString("expected_doc_ids")),
                splitLines(rs.getString("expected_chunk_ids")),
                rs.getInt("top_k"),
                splitLines(rs.getString("modes")),
                rs.getBoolean("enabled"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private RagExperimentRunResponse mapRun(ResultSet rs, int rowNum) throws SQLException {
        return new RagExperimentRunResponse(
                rs.getString("tenant_id"),
                rs.getString("run_id"),
                rs.getString("experiment_id"),
                rs.getString("mode"),
                rs.getString("status"),
                rs.getBigDecimal("recall_at_k"),
                rs.getBigDecimal("precision_at_k"),
                rs.getBigDecimal("mrr"),
                rs.getBigDecimal("ndcg"),
                rs.getInt("hit_count"),
                rs.getInt("expected_total"),
                rs.getLong("latency_ms"),
                splitLines(rs.getString("top_doc_ids")),
                splitLines(rs.getString("top_chunk_ids")),
                rs.getString("metrics_json"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private List<String> resolveModes(List<String> modes) {
        List<String> values = modes == null || modes.isEmpty() ? DEFAULT_MODES : modes;
        return values.stream()
                .map(mode -> mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT))
                .filter(mode -> !mode.isBlank())
                .peek(this::options)
                .distinct()
                .toList();
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private List<String> distinct(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private String joinLines(List<String> values) {
        return String.join("\n", values == null ? List.of() : values);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String resolveExperimentId(String experimentId) {
        String value = experimentId == null || experimentId.isBlank()
                ? "raglab-" + UUID.randomUUID().toString().substring(0, 8)
                : experimentId.trim();
        if (!value.matches("[a-zA-Z0-9._-]{3,128}")) {
            throw new IllegalArgumentException("experimentId 只能包含字母、数字、点、下划线和中划线，长度 3-128");
        }
        return value;
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private record RagMetrics(
            BigDecimal recallAtK,
            BigDecimal precisionAtK,
            BigDecimal mrr,
            BigDecimal ndcg,
            int hitCount,
            int expectedTotal
    ) {
    }
}
