CREATE TABLE IF NOT EXISTS ai_eval_case_candidate_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    audit_id VARCHAR(128) NOT NULL,
    candidate_id VARCHAR(128) NOT NULL,
    feedback_id VARCHAR(128),
    action_type VARCHAR(64) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    review_status VARCHAR(32),
    summary VARCHAR(512) NOT NULL,
    detail_json LONGTEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uq_ai_eval_case_candidate_audit_tenant_id
    ON ai_eval_case_candidate_audit (tenant_id, audit_id);

CREATE INDEX idx_ai_eval_case_candidate_audit_candidate
    ON ai_eval_case_candidate_audit (tenant_id, candidate_id, created_at);

CREATE INDEX idx_ai_eval_case_candidate_audit_action
    ON ai_eval_case_candidate_audit (tenant_id, action_type, created_at);
