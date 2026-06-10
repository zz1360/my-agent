package com.superagent.logistics.knowledge;

import com.superagent.logistics.api.dto.KnowledgeIndexJobResponse;
import com.superagent.logistics.api.dto.KnowledgeReindexResponse;
import com.superagent.logistics.security.AgentUserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class KnowledgeIndexJobService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexJobService.class);

    private final JdbcTemplate jdbcTemplate;
    private final PgVectorKnowledgeStore vectorKnowledgeStore;

    public KnowledgeIndexJobService(JdbcTemplate jdbcTemplate, PgVectorKnowledgeStore vectorKnowledgeStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.vectorKnowledgeStore = vectorKnowledgeStore;
    }

    public KnowledgeIndexJobResponse enqueueAfterCommit(AgentUserContext context, String triggerType,
                                                        String documentId, String baseDocId) {
        KnowledgeIndexJobResponse job = createJob(context, triggerType, documentId, baseDocId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runAsync(job.jobId());
                }
            });
        } else {
            runAsync(job.jobId());
        }
        return job;
    }

    public KnowledgeReindexResponse enqueueManualReindex(AgentUserContext context) {
        KnowledgeIndexJobResponse job = enqueueAfterCommit(context, "MANUAL_REINDEX", null, null);
        return new KnowledgeReindexResponse(context.tenantId(), job.jobId(), job.status(), job.chunkCount(),
                job.vectorEnabled(), job.vectorReady(), job.tableName());
    }

    public List<KnowledgeIndexJobResponse> list(String tenantId, int limit) {
        return jdbcTemplate.query("""
                SELECT *
                FROM ai_knowledge_index_job
                WHERE tenant_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """, this::mapJob, tenantId, Math.max(1, Math.min(limit, 100)));
    }

    public KnowledgeIndexJobResponse get(String tenantId, String jobId) {
        List<KnowledgeIndexJobResponse> jobs = jdbcTemplate.query("""
                SELECT *
                FROM ai_knowledge_index_job
                WHERE tenant_id = ? AND job_id = ?
                """, this::mapJob, tenantId, jobId);
        return jobs.stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到知识索引任务：" + jobId));
    }

    private KnowledgeIndexJobResponse createJob(AgentUserContext context, String triggerType,
                                                String documentId, String baseDocId) {
        String jobId = "kb-index-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO ai_knowledge_index_job
                (tenant_id, job_id, trigger_type, requested_by, status, document_id, base_doc_id,
                 chunk_count, vector_enabled, vector_ready, table_name, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, context.tenantId(), jobId, triggerType, context.userId(), "QUEUED",
                documentId, baseDocId, 0, vectorKnowledgeStore.isEnabled(), vectorKnowledgeStore.isReady(),
                vectorKnowledgeStore.table(), Timestamp.from(now));
        return get(context.tenantId(), jobId);
    }

    private void runAsync(String jobId) {
        CompletableFuture.runAsync(() -> run(jobId));
    }

    private void run(String jobId) {
        Instant started = Instant.now();
        try {
            jdbcTemplate.update("""
                    UPDATE ai_knowledge_index_job
                    SET status = 'RUNNING', started_at = ?
                    WHERE job_id = ?
                    """, Timestamp.from(started), jobId);
            KnowledgeIndexJobResponse job = findByJobId(jobId);
            List<KnowledgeChunk> chunks = activeChunks(job.tenantId());
            vectorKnowledgeStore.syncChunks(job.tenantId(), chunks);
            Instant finished = Instant.now();
            jdbcTemplate.update("""
                    UPDATE ai_knowledge_document
                    SET indexed_at = ?
                    WHERE tenant_id = ? AND status = 'ACTIVE'
                    """, Timestamp.from(finished), job.tenantId());
            jdbcTemplate.update("""
                    UPDATE ai_knowledge_index_job
                    SET status = 'COMPLETED', chunk_count = ?, vector_enabled = ?, vector_ready = ?,
                        table_name = ?, finished_at = ?, error_message = NULL
                    WHERE job_id = ?
                    """, chunks.size(), vectorKnowledgeStore.isEnabled(), vectorKnowledgeStore.isReady(),
                    vectorKnowledgeStore.table(), Timestamp.from(finished), jobId);
        } catch (Exception ex) {
            Instant failedAt = Instant.now();
            jdbcTemplate.update("""
                    UPDATE ai_knowledge_index_job
                    SET status = 'FAILED', error_message = ?, finished_at = ?
                    WHERE job_id = ?
                    """, ex.getMessage(), Timestamp.from(failedAt), jobId);
            log.warn("Knowledge index job failed: jobId={}, error={}", jobId, ex.getMessage());
        }
    }

    private KnowledgeIndexJobResponse findByJobId(String jobId) {
        List<KnowledgeIndexJobResponse> jobs = jdbcTemplate.query("""
                SELECT *
                FROM ai_knowledge_index_job
                WHERE job_id = ?
                """, this::mapJob, jobId);
        return jobs.stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到知识索引任务：" + jobId));
    }

    private List<KnowledgeChunk> activeChunks(String tenantId) {
        return jdbcTemplate.query("""
                SELECT c.doc_id, c.chunk_id, c.title_path, c.content, c.metadata, c.acl_roles
                FROM ai_knowledge_chunk c
                JOIN ai_knowledge_document d
                  ON d.tenant_id = c.tenant_id AND d.doc_id = c.doc_id
                WHERE c.tenant_id = ?
                  AND d.status = 'ACTIVE'
                  AND (d.effective_from IS NULL OR d.effective_from <= CURRENT_DATE)
                  AND (d.effective_to IS NULL OR d.effective_to >= CURRENT_DATE)
                ORDER BY c.id
                """, (rs, rowNum) -> new KnowledgeChunk(
                rs.getString("doc_id"),
                rs.getString("chunk_id"),
                rs.getString("title_path"),
                rs.getString("content"),
                rs.getString("metadata"),
                rs.getString("acl_roles")
        ), tenantId);
    }

    private KnowledgeIndexJobResponse mapJob(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgeIndexJobResponse(
                rs.getString("tenant_id"),
                rs.getString("job_id"),
                rs.getString("trigger_type"),
                rs.getString("requested_by"),
                rs.getString("status"),
                rs.getString("document_id"),
                rs.getString("base_doc_id"),
                rs.getInt("chunk_count"),
                rs.getBoolean("vector_enabled"),
                rs.getBoolean("vector_ready"),
                rs.getString("table_name"),
                rs.getString("error_message"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("finished_at"))
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
