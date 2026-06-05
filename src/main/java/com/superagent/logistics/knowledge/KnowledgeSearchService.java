package com.superagent.logistics.knowledge;

import com.superagent.logistics.security.AgentPermissionService;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

    public KnowledgeSearchService(JdbcTemplate jdbcTemplate,
                                  AgentPermissionService permissionService,
                                  PgVectorKnowledgeStore vectorKnowledgeStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionService = permissionService;
        this.vectorKnowledgeStore = vectorKnowledgeStore;
    }

    public List<KnowledgeSearchResult> search(AgentUserContext context, String query, int topK) {
        if (vectorKnowledgeStore.isReady()) {
            List<KnowledgeSearchResult> vectorResults = vectorKnowledgeStore.search(context.tenantId(), query, topK)
                    .stream()
                    .filter(result -> permissionService.canReadKnowledge(context, result.chunk().aclRoles()))
                    .limit(Math.max(1, Math.min(topK, 8)))
                    .toList();
            if (!vectorResults.isEmpty()) {
                return vectorResults;
            }
        }
        List<KnowledgeChunk> chunks = jdbcTemplate.query("""
                SELECT c.doc_id, c.chunk_id, c.title_path, c.content, c.metadata, c.acl_roles
                FROM ai_knowledge_chunk c
                JOIN ai_knowledge_document d
                  ON d.tenant_id = c.tenant_id AND d.doc_id = c.doc_id
                WHERE c.tenant_id = ? AND d.status = 'ACTIVE'
                """, this::mapChunk, context.tenantId());
        Set<String> terms = extractTerms(query);
        return chunks.stream()
                .filter(chunk -> permissionService.canReadKnowledge(context, chunk.aclRoles()))
                .map(chunk -> new KnowledgeSearchResult(chunk, score(chunk, terms, query)))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingDouble(KnowledgeSearchResult::score).reversed())
                .limit(Math.max(1, Math.min(topK, 8)))
                .toList();
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

    private double score(KnowledgeChunk chunk, Set<String> terms, String originalQuery) {
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
}
