CREATE TABLE ai_agent_rag_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    query_text LONGTEXT NOT NULL,
    retrieval_mode VARCHAR(64) NOT NULL,
    knowledge_version VARCHAR(1024),
    top_k INT NOT NULL,
    vector_ready TINYINT(1) NOT NULL,
    vector_used TINYINT(1) NOT NULL,
    keyword_used TINYINT(1) NOT NULL,
    reranker_used TINYINT(1) NOT NULL,
    active_chunk_count INT NOT NULL,
    candidate_count INT NOT NULL,
    rerank_candidate_count INT NOT NULL,
    returned_count INT NOT NULL,
    hits_json LONGTEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_ai_agent_rag_audit_trace
    ON ai_agent_rag_audit (trace_id);

CREATE INDEX idx_ai_agent_rag_audit_tenant_mode
    ON ai_agent_rag_audit (tenant_id, retrieval_mode, created_at);
