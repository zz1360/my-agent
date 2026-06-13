CREATE TABLE IF NOT EXISTS ai_eval_case_candidate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    candidate_id VARCHAR(128) NOT NULL,
    feedback_id VARCHAR(128) NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    message_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128),
    source_question LONGTEXT,
    source_answer LONGTEXT NOT NULL,
    rating VARCHAR(32) NOT NULL,
    reason VARCHAR(128),
    endpoint VARCHAR(64) NOT NULL,
    eval_type VARCHAR(32) NOT NULL,
    expected_contains LONGTEXT,
    expected_citations LONGTEXT,
    expected_rag_doc_ids LONGTEXT,
    expected_rag_chunk_ids LONGTEXT,
    expected_min_tool_calls INT NOT NULL DEFAULT 0,
    expected_top_k INT NOT NULL DEFAULT 5,
    rag_query LONGTEXT,
    status VARCHAR(32) NOT NULL,
    eval_case_id VARCHAR(128),
    rag_experiment_id VARCHAR(128),
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uq_ai_eval_case_candidate_tenant_id ON ai_eval_case_candidate (tenant_id, candidate_id);
CREATE UNIQUE INDEX uq_ai_eval_case_candidate_feedback ON ai_eval_case_candidate (tenant_id, feedback_id);
CREATE INDEX idx_ai_eval_case_candidate_status ON ai_eval_case_candidate (tenant_id, status, created_at);
CREATE INDEX idx_ai_eval_case_candidate_trace ON ai_eval_case_candidate (trace_id);
