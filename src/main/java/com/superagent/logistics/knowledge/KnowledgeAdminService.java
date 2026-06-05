package com.superagent.logistics.knowledge;

import com.superagent.logistics.api.dto.Citation;
import com.superagent.logistics.api.dto.KnowledgeChunkResponse;
import com.superagent.logistics.api.dto.KnowledgeDocumentRequest;
import com.superagent.logistics.api.dto.KnowledgeDocumentResponse;
import com.superagent.logistics.api.dto.KnowledgeReindexResponse;
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
    private final PgVectorKnowledgeStore vectorKnowledgeStore;

    public KnowledgeAdminService(JdbcTemplate jdbcTemplate,
                                 AgentPermissionService permissionService,
                                 KnowledgeSearchService searchService,
                                 PgVectorKnowledgeStore vectorKnowledgeStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionService = permissionService;
        this.searchService = searchService;
        this.vectorKnowledgeStore = vectorKnowledgeStore;
    }

    @Transactional
    public KnowledgeDocumentResponse upsert(KnowledgeDocumentRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        checkManageable(context);
        String docId = resolveDocId(request.docId());
        String aclRoles = resolveAclRoles(request.aclRoles());
        Instant now = Instant.now();

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
                (tenant_id, doc_id, title, doc_type, biz_domain, version, source_url, acl_roles,
                 effective_from, effective_to, status, content, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, context.tenantId(), docId, request.title().trim(), request.docType().trim(),
                request.bizDomain().trim(), blankToNull(request.version()), blankToNull(request.sourceUrl()),
                aclRoles, toSqlDate(request.effectiveFrom()), toSqlDate(request.effectiveTo()), "ACTIVE",
                request.content().trim(), Timestamp.from(now), Timestamp.from(now));

        List<String> chunks = splitContent(request.content());
        for (int i = 0; i < chunks.size(); i++) {
            String chunkId = "%s-chunk-%03d".formatted(docId, i + 1);
            jdbcTemplate.update("""
                    INSERT INTO ai_knowledge_chunk
                    (tenant_id, doc_id, chunk_id, title_path, content, metadata, acl_roles, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, context.tenantId(), docId, chunkId, request.title().trim() + " / " + (i + 1),
                    chunks.get(i), "docType=" + request.docType().trim() + ";version=" + nullToEmpty(request.version())
                            + ";bizDomain=" + request.bizDomain().trim() + ";chunkIndex=" + (i + 1),
                    aclRoles, Timestamp.from(now));
        }
        reindex(context.tenantId(), context);
        return get(context.tenantId(), docId, context, true);
    }

    public List<KnowledgeDocumentResponse> list(String tenantId, String userId, List<String> roles,
                                                String status, String bizDomain, int limit) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        StringBuilder sql = new StringBuilder("""
                SELECT d.*,
                       (SELECT COUNT(*) FROM ai_knowledge_chunk c WHERE c.tenant_id = d.tenant_id AND c.doc_id = d.doc_id) AS chunk_count
                FROM ai_knowledge_document d
                WHERE d.tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(context.tenantId());
        if (status != null && !status.isBlank()) {
            sql.append(" AND d.status = ?");
            args.add(status);
        }
        if (bizDomain != null && !bizDomain.isBlank()) {
            sql.append(" AND d.biz_domain = ?");
            args.add(bizDomain);
        }
        sql.append(" ORDER BY d.updated_at DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 100)));
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapDocument(rs, List.of()), args.toArray());
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
        int updated = jdbcTemplate.update("""
                UPDATE ai_knowledge_document
                SET status = 'DISABLED', updated_at = ?
                WHERE tenant_id = ? AND doc_id = ?
                """, Timestamp.from(Instant.now()), context.tenantId(), docId);
        if (updated == 0) {
            throw new IllegalArgumentException("未找到知识文档：" + docId);
        }
        jdbcTemplate.update("""
                DELETE FROM ai_knowledge_chunk
                WHERE tenant_id = ? AND doc_id = ?
                """, context.tenantId(), docId);
        reindex(context.tenantId(), context);
        return get(context.tenantId(), docId, context, true);
    }

    public KnowledgeReindexResponse reindex(String tenantId, String userId, List<String> roles) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        checkManageable(context);
        return reindex(context.tenantId(), context);
    }

    public List<Citation> search(String tenantId, String userId, List<String> roles, String query, int topK) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        return searchService.search(context, query, topK).stream()
                .map(result -> new Citation("knowledge", result.chunk().title(), result.chunk().docId(),
                        result.chunk().chunkId(), excerpt(result.chunk().content(), 160)))
                .toList();
    }

    private KnowledgeReindexResponse reindex(String tenantId, AgentUserContext context) {
        List<KnowledgeChunk> chunks = jdbcTemplate.query("""
                SELECT c.doc_id, c.chunk_id, c.title_path, c.content, c.metadata, c.acl_roles
                FROM ai_knowledge_chunk c
                JOIN ai_knowledge_document d
                  ON d.tenant_id = c.tenant_id AND d.doc_id = c.doc_id
                WHERE c.tenant_id = ? AND d.status = 'ACTIVE'
                ORDER BY c.id
                """, this::mapChunk, tenantId);
        vectorKnowledgeStore.syncChunks(tenantId, chunks);
        return new KnowledgeReindexResponse(tenantId, chunks.size(), vectorKnowledgeStore.isEnabled(),
                vectorKnowledgeStore.isReady(), vectorKnowledgeStore.table());
    }

    private KnowledgeDocumentResponse get(String tenantId, String docId, AgentUserContext context, boolean includeChunks) {
        List<KnowledgeDocumentResponse> docs = jdbcTemplate.query("""
                SELECT d.*,
                       (SELECT COUNT(*) FROM ai_knowledge_chunk c WHERE c.tenant_id = d.tenant_id AND c.doc_id = d.doc_id) AS chunk_count
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
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
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

    private void checkManageable(AgentUserContext context) {
        if (!context.hasAnyRole("ADMIN", "OPS_MANAGER", "OPERATIONS")) {
            throw new AccessDeniedException("当前用户没有知识库运营权限");
        }
    }

    private String resolveDocId(String docId) {
        String value = docId == null || docId.isBlank() ? "kb-" + UUID.randomUUID().toString().substring(0, 8) : docId.trim();
        if (!value.matches("[a-zA-Z0-9._-]{3,128}")) {
            throw new IllegalArgumentException("docId 只能包含字母、数字、点、下划线和中划线，长度 3-128");
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String excerpt(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        String compact = content.replaceAll("\\s+", " ").trim();
        return compact.length() <= maxLength ? compact : compact.substring(0, maxLength) + "...";
    }
}
