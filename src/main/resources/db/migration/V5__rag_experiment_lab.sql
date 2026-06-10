CREATE TABLE IF NOT EXISTS ai_rag_experiment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    experiment_id VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description LONGTEXT,
    user_id VARCHAR(64) NOT NULL,
    roles VARCHAR(512) NOT NULL,
    query LONGTEXT NOT NULL,
    expected_doc_ids LONGTEXT,
    expected_chunk_ids LONGTEXT,
    top_k INT NOT NULL,
    modes VARCHAR(512) NOT NULL,
    enabled TINYINT(1) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_rag_experiment_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    experiment_id VARCHAR(128) NOT NULL,
    mode VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    recall_at_k DECIMAL(8,4),
    precision_at_k DECIMAL(8,4),
    mrr DECIMAL(8,4),
    ndcg DECIMAL(8,4),
    hit_count INT NOT NULL DEFAULT 0,
    expected_total INT NOT NULL DEFAULT 0,
    latency_ms BIGINT,
    top_doc_ids LONGTEXT,
    top_chunk_ids LONGTEXT,
    metrics_json LONGTEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uq_ai_rag_experiment_tenant_id ON ai_rag_experiment (tenant_id, experiment_id);
CREATE INDEX idx_ai_rag_experiment_enabled ON ai_rag_experiment (tenant_id, enabled);
CREATE UNIQUE INDEX uq_ai_rag_experiment_run_id ON ai_rag_experiment_run (run_id);
CREATE INDEX idx_ai_rag_experiment_run_exp ON ai_rag_experiment_run (tenant_id, experiment_id, created_at);
