ALTER TABLE ai_eval_case
    ADD COLUMN eval_type VARCHAR(32) NOT NULL DEFAULT 'AGENT';

ALTER TABLE ai_eval_case
    ADD COLUMN rag_query LONGTEXT;

ALTER TABLE ai_eval_case
    ADD COLUMN expected_rag_doc_ids LONGTEXT;

ALTER TABLE ai_eval_case
    ADD COLUMN expected_rag_chunk_ids LONGTEXT;

ALTER TABLE ai_eval_case
    ADD COLUMN expected_top_k INT NOT NULL DEFAULT 5;

ALTER TABLE ai_eval_case_result
    ADD COLUMN rag_hit_rate DECIMAL(8,4);

ALTER TABLE ai_eval_case_result
    ADD COLUMN rag_top_doc_ids LONGTEXT;

ALTER TABLE ai_eval_case_result
    ADD COLUMN rag_top_chunk_ids LONGTEXT;

ALTER TABLE ai_eval_case_result
    ADD COLUMN rag_metrics_json LONGTEXT;

CREATE INDEX idx_ai_eval_case_type ON ai_eval_case (tenant_id, eval_type, enabled);
