ALTER TABLE ai_knowledge_document
    ADD COLUMN base_doc_id VARCHAR(128);

ALTER TABLE ai_knowledge_document
    ADD COLUMN published_at TIMESTAMP;

ALTER TABLE ai_knowledge_document
    ADD COLUMN indexed_at TIMESTAMP;

UPDATE ai_knowledge_document
SET base_doc_id = doc_id
WHERE base_doc_id IS NULL;

CREATE TABLE IF NOT EXISTS ai_knowledge_index_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    job_id VARCHAR(128) NOT NULL,
    trigger_type VARCHAR(64) NOT NULL,
    requested_by VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    document_id VARCHAR(128),
    base_doc_id VARCHAR(128),
    chunk_count INT NOT NULL DEFAULT 0,
    vector_enabled TINYINT(1) NOT NULL,
    vector_ready TINYINT(1) NOT NULL,
    table_name VARCHAR(128),
    error_message LONGTEXT,
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE UNIQUE INDEX uq_ai_knowledge_index_job_id ON ai_knowledge_index_job (job_id);
CREATE INDEX idx_ai_knowledge_index_job_tenant ON ai_knowledge_index_job (tenant_id, created_at);
CREATE INDEX idx_ai_knowledge_document_base ON ai_knowledge_document (tenant_id, base_doc_id, status);
