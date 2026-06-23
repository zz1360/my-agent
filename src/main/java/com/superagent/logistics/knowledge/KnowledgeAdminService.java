package com.superagent.logistics.knowledge;

import com.superagent.logistics.api.dto.Citation;
import com.superagent.logistics.api.dto.KnowledgeIndexJobResponse;
import com.superagent.logistics.api.dto.KnowledgeChunkResponse;
import com.superagent.logistics.api.dto.KnowledgeDocumentRequest;
import com.superagent.logistics.api.dto.KnowledgeDocumentResponse;
import com.superagent.logistics.api.dto.KnowledgePreviewResponse;
import com.superagent.logistics.api.dto.KnowledgeReindexResponse;
import com.superagent.logistics.api.dto.KnowledgeSearchHitResponse;
import com.superagent.logistics.api.dto.KnowledgeSearchPreviewResponse;
import com.superagent.logistics.api.dto.RetrievalStatusResponse;
import com.superagent.logistics.api.dto.PageResponse;
import com.superagent.logistics.security.AccessDeniedException;
import com.superagent.logistics.security.AgentPermissionService;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class KnowledgeAdminService {

    private static final int CHUNK_SIZE = 420;

    private final JdbcTemplate jdbcTemplate;
    private final AgentPermissionService permissionService;
    private final KnowledgeSearchService searchService;
    private final KnowledgeIndexJobService indexJobService;
    private final PgVectorKnowledgeStore vectorKnowledgeStore;

    public KnowledgeAdminService(JdbcTemplate jdbcTemplate,
                                 AgentPermissionService permissionService,
                                 KnowledgeSearchService searchService,
                                 KnowledgeIndexJobService indexJobService,
                                 PgVectorKnowledgeStore vectorKnowledgeStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionService = permissionService;
        this.searchService = searchService;
        this.indexJobService = indexJobService;
        this.vectorKnowledgeStore = vectorKnowledgeStore;
    }

    @Transactional
    public KnowledgeDocumentResponse upsert(KnowledgeDocumentRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        checkManageable(context);
        String baseDocId = resolveBaseDocId(request.baseDocId(), request.docId());
        String docId = resolveDocId(request.docId(), baseDocId, request.version());
        String aclRoles = resolveAclRoles(request.aclRoles());
        String status = resolveStatus(request.status());
        Instant now = Instant.now();
        Timestamp publishedAt = "ACTIVE".equals(status) ? Timestamp.from(now) : null;

        jdbcTemplate.update("""
                DELETE FROM ai_knowledge_chunk
                WHERE tenant_id = ? AND doc_id = ?
                """, context.tenantId(), docId);
        jdbcTemplate.update("""
                DELETE FROM ai_knowledge_document
                WHERE tenant_id = ? AND doc_id = ?
                """, context.tenantId(), docId);
        jdbcTemplate.update("""
                INSERT INTO ai_knowledge_document
                (tenant_id, doc_id, base_doc_id, title, doc_type, biz_domain, version, source_url, acl_roles,
                 effective_from, effective_to, status, content, published_at, indexed_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, context.tenantId(), docId, baseDocId, request.title().trim(), request.docType().trim(),
                request.bizDomain().trim(), blankToNull(request.version()), blankToNull(request.sourceUrl()),
                aclRoles, toSqlDate(request.effectiveFrom()), toSqlDate(request.effectiveTo()), status,
                request.content().trim(), publishedAt, null, Timestamp.from(now), Timestamp.from(now));

        List<String> chunks = splitContent(request.content());
        insertChunks(context.tenantId(), docId, baseDocId, request, aclRoles, chunks, now);
        if (request.autoIndex() == null || request.autoIndex()) {
            indexJobService.enqueueAfterCommit(context, "DOCUMENT_UPSERT", docId, baseDocId);
        }
        return get(context.tenantId(), docId, context, true);
    }

    public KnowledgePreviewResponse preview(KnowledgeDocumentRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        checkManageable(context);
        String baseDocId = resolveBaseDocId(request.baseDocId(), request.docId());
        String docId = resolveDocId(request.docId(), baseDocId, request.version());
        String aclRoles = resolveAclRoles(request.aclRoles());
        List<String> chunks = splitContent(request.content());
        List<KnowledgeChunkResponse> previews = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            previews.add(new KnowledgeChunkResponse(
                    docId,
                    "%s-chunk-%03d".formatted(docId, i + 1),
                    request.title().trim() + " / " + (i + 1),
                    excerpt(chunks.get(i), 220),
                    metadata(baseDocId, request, i + 1),
                    aclRoles
            ));
        }
        return new KnowledgePreviewResponse(context.tenantId(), baseDocId, docId, request.title().trim(),
                request.docType().trim(), request.bizDomain().trim(), blankToNull(request.version()),
                previews.size(), previews);
    }

    public List<KnowledgeDocumentResponse> list(String tenantId, String userId, List<String> roles,
                                                String status, String bizDomain, String baseDocId, int limit) {
        return page(tenantId, userId, roles, status, bizDomain, baseDocId, 1, limit).items();
    }

    public PageResponse<KnowledgeDocumentResponse> page(String tenantId, String userId, List<String> roles,
                                                         String status, String bizDomain, String baseDocId,
                                                         int page, int size) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        int resolvedPage = PageResponse.normalizePage(page);
        int resolvedSize = PageResponse.normalizeSize(size);
        String select = """
                SELECT d.*,
                       (SELECT COUNT(*) FROM ai_knowledge_chunk c WHERE c.tenant_id = d.tenant_id AND c.doc_id = d.doc_id) AS chunk_count,
                       (SELECT j.job_id FROM ai_knowledge_index_job j
                         WHERE j.tenant_id = d.tenant_id
                           AND (j.document_id = d.doc_id OR j.base_doc_id = d.base_doc_id)
                         ORDER BY j.created_at DESC LIMIT 1) AS index_job_id
                FROM ai_knowledge_document d
                """;
        StringBuilder where = new StringBuilder(" WHERE d.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(context.tenantId());
        if (status != null && !status.isBlank()) {
            where.append(" AND d.status = ?");
            args.add(status);
        }
        if (bizDomain != null && !bizDomain.isBlank()) {
            where.append(" AND d.biz_domain = ?");
            args.add(bizDomain);
        }
        if (baseDocId != null && !baseDocId.isBlank()) {
            where.append(" AND d.base_doc_id = ?");
            args.add(baseDocId);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_knowledge_document d" + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(resolvedSize);
        pageArgs.add((resolvedPage - 1) * resolvedSize);
        List<KnowledgeDocumentResponse> items = jdbcTemplate.query(
                select + where + " ORDER BY d.updated_at DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> mapDocument(rs, List.of()), pageArgs.toArray());
        return PageResponse.of(items, resolvedPage, resolvedSize, total == null ? 0 : total);
    }

    public KnowledgeDocumentResponse get(String tenantId, String docId, String userId, List<String> roles) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        return get(context.tenantId(), docId, context, true);
    }

    @Transactional
    public KnowledgeDocumentResponse disable(String tenantId, String docId, String userId, List<String> roles) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        checkManageable(context);
        String baseDocId = findBaseDocId(context.tenantId(), docId);
        int updated = jdbcTemplate.update("""
                UPDATE ai_knowledge_document
                SET status = 'DISABLED', updated_at = ?
                WHERE tenant_id = ? AND doc_id = ?
                """, Timestamp.from(Instant.now()), context.tenantId(), docId);
        if (updated == 0) {
            throw new IllegalArgumentException("未找到知识文档：" + docId);
        }
        indexJobService.enqueueAfterCommit(context, "DOCUMENT_DISABLE", docId, baseDocId);
        return get(context.tenantId(), docId, context, true);
    }

    @Transactional
    public KnowledgeDocumentResponse publish(String tenantId, String docId, String userId, List<String> roles) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        checkManageable(context);
        String baseDocId = findBaseDocId(context.tenantId(), docId);
        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE ai_knowledge_document
                SET status = 'EXPIRED', effective_to = COALESCE(effective_to, CURRENT_DATE), updated_at = ?
                WHERE tenant_id = ? AND base_doc_id = ? AND doc_id <> ? AND status = 'ACTIVE'
                """, Timestamp.from(now), context.tenantId(), baseDocId, docId);
        int updated = jdbcTemplate.update("""
                UPDATE ai_knowledge_document
                SET status = 'ACTIVE', published_at = COALESCE(published_at, ?), updated_at = ?
                WHERE tenant_id = ? AND doc_id = ?
                """, Timestamp.from(now), Timestamp.from(now), context.tenantId(), docId);
        if (updated == 0) {
            throw new IllegalArgumentException("未找到知识文档：" + docId);
        }
        indexJobService.enqueueAfterCommit(context, "DOCUMENT_PUBLISH", docId, baseDocId);
        return get(context.tenantId(), docId, context, true);
    }

    @Transactional
    public KnowledgeDocumentResponse expire(String tenantId, String docId, String userId, List<String> roles) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        checkManageable(context);
        String baseDocId = findBaseDocId(context.tenantId(), docId);
        int updated = jdbcTemplate.update("""
                UPDATE ai_knowledge_document
                SET status = 'EXPIRED', effective_to = COALESCE(effective_to, CURRENT_DATE), updated_at = ?
                WHERE tenant_id = ? AND doc_id = ?
                """, Timestamp.from(Instant.now()), context.tenantId(), docId);
        if (updated == 0) {
            throw new IllegalArgumentException("未找到知识文档：" + docId);
        }
        indexJobService.enqueueAfterCommit(context, "DOCUMENT_EXPIRE", docId, baseDocId);
        return get(context.tenantId(), docId, context, true);
    }

    public KnowledgeReindexResponse reindex(String tenantId, String userId, List<String> roles) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        checkManageable(context);
        return indexJobService.enqueueManualReindex(context);
    }

    public List<KnowledgeIndexJobResponse> listIndexJobs(String tenantId, String userId, List<String> roles, int limit) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        checkManageable(context);
        return indexJobService.list(context.tenantId(), limit);
    }

    public KnowledgeIndexJobResponse getIndexJob(String tenantId, String userId, List<String> roles, String jobId) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        checkManageable(context);
        return indexJobService.get(context.tenantId(), jobId);
    }

    public List<Citation> search(String tenantId, String userId, List<String> roles, String query, int topK, String mode) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        return searchService.search(context, query, topK, KnowledgeSearchOptions.fromMode(mode)).stream()
                .map(result -> new Citation("knowledge", result.chunk().title(), result.chunk().docId(),
                        result.chunk().chunkId(), excerpt(result.chunk().content(), 160)))
                .toList();
    }

    public KnowledgeSearchPreviewResponse searchPreview(String tenantId, String userId, List<String> roles,
                                                        String query, int topK, String mode) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        String normalizedMode = KnowledgeSearchOptions.normalizeMode(mode);
        int resultLimit = Math.max(1, Math.min(topK, 20));
        List<KnowledgeSearchHitResponse> hits = searchService.search(context, query, resultLimit,
                        KnowledgeSearchOptions.fromMode(normalizedMode))
                .stream()
                .map(result -> new KnowledgeSearchHitResponse(
                        result.chunk().title(),
                        result.chunk().docId(),
                        result.chunk().chunkId(),
                        excerpt(result.chunk().content(), 180),
                        result.score(),
                        result.vectorScore(),
                        result.keywordScore(),
                        result.ruleScore(),
                        result.rerankerScore(),
                        result.rerankerProvider()
                ))
                .toList();
        return new KnowledgeSearchPreviewResponse(normalizedMode, vectorKnowledgeStore.isReady(), resultLimit, hits);
    }

    public RetrievalStatusResponse retrievalStatus() {
        return new RetrievalStatusResponse(
                searchService.defaultMode(),
                vectorKnowledgeStore.isEnabled(),
                vectorKnowledgeStore.isReady(),
                vectorKnowledgeStore.table(),
                vectorKnowledgeStore.countChunks()
        );
    }

    private KnowledgeDocumentResponse get(String tenantId, String docId, AgentUserContext context, boolean includeChunks) {
        List<KnowledgeDocumentResponse> docs = jdbcTemplate.query("""
                SELECT d.*,
                       (SELECT COUNT(*) FROM ai_knowledge_chunk c WHERE c.tenant_id = d.tenant_id AND c.doc_id = d.doc_id) AS chunk_count,
                       (SELECT j.job_id FROM ai_knowledge_index_job j
                         WHERE j.tenant_id = d.tenant_id
                           AND (j.document_id = d.doc_id OR j.base_doc_id = d.base_doc_id)
                         ORDER BY j.created_at DESC LIMIT 1) AS index_job_id
                FROM ai_knowledge_document d
                WHERE d.tenant_id = ? AND d.doc_id = ?
                """, (rs, rowNum) -> mapDocument(rs, includeChunks ? findChunks(tenantId, docId) : List.of()),
                tenantId, docId);
        KnowledgeDocumentResponse response = docs.stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到知识文档：" + docId));
        if (!permissionService.canReadKnowledge(context, response.aclRoles())) {
            throw new AccessDeniedException("当前用户无权访问知识文档 " + docId);
        }
        return response;
    }

    private List<KnowledgeChunkResponse> findChunks(String tenantId, String docId) {
        return jdbcTemplate.query("""
                SELECT doc_id, chunk_id, title_path, content, metadata, acl_roles
                FROM ai_knowledge_chunk
                WHERE tenant_id = ? AND doc_id = ?
                ORDER BY id
                """, (rs, rowNum) -> new KnowledgeChunkResponse(
                rs.getString("doc_id"),
                rs.getString("chunk_id"),
                rs.getString("title_path"),
                excerpt(rs.getString("content"), 220),
                rs.getString("metadata"),
                rs.getString("acl_roles")
        ), tenantId, docId);
    }

    private KnowledgeDocumentResponse mapDocument(ResultSet rs, List<KnowledgeChunkResponse> chunks) throws SQLException {
        return new KnowledgeDocumentResponse(
                rs.getString("tenant_id"),
                rs.getString("doc_id"),
                rs.getString("base_doc_id"),
                rs.getString("title"),
                rs.getString("doc_type"),
                rs.getString("biz_domain"),
                rs.getString("version"),
                rs.getString("source_url"),
                rs.getString("acl_roles"),
                toLocalDate(rs.getDate("effective_from")),
                toLocalDate(rs.getDate("effective_to")),
                rs.getString("status"),
                rs.getString("content"),
                rs.getInt("chunk_count"),
                chunks,
                rs.getString("index_job_id"),
                toInstant(rs.getTimestamp("published_at")),
                toInstant(rs.getTimestamp("indexed_at")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private void checkManageable(AgentUserContext context) {
        if (!context.hasAnyRole("ADMIN", "OPS_MANAGER", "OPERATIONS")) {
            throw new AccessDeniedException("当前用户没有知识库运营权限");
        }
    }

    private String resolveBaseDocId(String baseDocId, String docId) {
        String value = firstNotBlank(baseDocId, docId, "kb-" + UUID.randomUUID().toString().substring(0, 8));
        return validateId(value, "baseDocId");
    }

    private String resolveDocId(String docId, String baseDocId, String version) {
        String value = docId;
        if (value == null || value.isBlank()) {
            String suffix = version == null || version.isBlank()
                    ? UUID.randomUUID().toString().substring(0, 8)
                    : version.trim().replaceAll("[^a-zA-Z0-9._-]+", "-");
            value = baseDocId + "-" + suffix;
        }
        return validateId(value, "docId");
    }

    private String validateId(String rawValue, String fieldName) {
        String value = rawValue.trim();
        if (!value.matches("[a-zA-Z0-9._-]{3,128}")) {
            throw new IllegalArgumentException(fieldName + " 只能包含字母、数字、点、下划线和中划线，长度 3-128");
        }
        return value;
    }

    private String resolveStatus(String status) {
        String value = status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DRAFT", "ACTIVE", "DISABLED", "EXPIRED").contains(value)) {
            throw new IllegalArgumentException("知识文档状态只能是 DRAFT、ACTIVE、DISABLED 或 EXPIRED");
        }
        return value;
    }

    private String resolveAclRoles(List<String> aclRoles) {
        if (aclRoles == null || aclRoles.isEmpty()) {
            return "PUBLIC";
        }
        return String.join(",", aclRoles.stream()
                .map(role -> role == null ? "" : role.trim().toUpperCase(Locale.ROOT))
                .filter(role -> !role.isBlank())
                .toList());
    }

    private List<String> splitContent(String rawContent) {
        String content = rawContent == null ? "" : rawContent.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : content.split("\\n\\s*\\n")) {
            String normalized = paragraph.replaceAll("\\s+", " ").trim();
            if (normalized.isBlank()) {
                continue;
            }
            if (current.length() + normalized.length() + 1 > CHUNK_SIZE && !current.isEmpty()) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (normalized.length() > CHUNK_SIZE) {
                for (int start = 0; start < normalized.length(); start += CHUNK_SIZE) {
                    int end = Math.min(normalized.length(), start + CHUNK_SIZE);
                    chunks.add(normalized.substring(start, end));
                }
            } else {
                if (!current.isEmpty()) {
                    current.append("\n");
                }
                current.append(normalized);
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks.isEmpty() ? List.of(content) : chunks;
    }

    private Date toSqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private LocalDate toLocalDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private void insertChunks(String tenantId, String docId, String baseDocId, KnowledgeDocumentRequest request,
                              String aclRoles, List<String> chunks, Instant now) {
        for (int i = 0; i < chunks.size(); i++) {
            String chunkId = "%s-chunk-%03d".formatted(docId, i + 1);
            jdbcTemplate.update("""
                    INSERT INTO ai_knowledge_chunk
                    (tenant_id, doc_id, chunk_id, title_path, content, metadata, acl_roles, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, tenantId, docId, chunkId, request.title().trim() + " / " + (i + 1),
                    chunks.get(i), metadata(baseDocId, request, i + 1), aclRoles, Timestamp.from(now));
        }
    }

    private String metadata(String baseDocId, KnowledgeDocumentRequest request, int chunkIndex) {
        return "baseDocId=" + baseDocId
                + ";docType=" + request.docType().trim()
                + ";version=" + nullToEmpty(request.version())
                + ";bizDomain=" + request.bizDomain().trim()
                + ";chunkIndex=" + chunkIndex;
    }

    private String findBaseDocId(String tenantId, String docId) {
        List<String> baseDocIds = jdbcTemplate.query("""
                SELECT base_doc_id
                FROM ai_knowledge_document
                WHERE tenant_id = ? AND doc_id = ?
                """, (rs, rowNum) -> rs.getString("base_doc_id"), tenantId, docId);
        return baseDocIds.stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到知识文档：" + docId));
    }

    private String excerpt(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        String compact = content.replaceAll("\\s+", " ").trim();
        return compact.length() <= maxLength ? compact : compact.substring(0, maxLength) + "...";
    }
}
