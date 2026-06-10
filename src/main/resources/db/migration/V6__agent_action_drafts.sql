CREATE TABLE IF NOT EXISTS ai_agent_action_draft (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    action_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128),
    conversation_id VARCHAR(128),
    customer_id VARCHAR(32) NOT NULL,
    waybill_id VARCHAR(64),
    action_type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    priority VARCHAR(32) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    draft_content LONGTEXT NOT NULL,
    evidence_json LONGTEXT,
    created_by VARCHAR(64) NOT NULL,
    reviewer_id VARCHAR(64),
    review_comment LONGTEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP
);

CREATE UNIQUE INDEX uq_ai_agent_action_draft_id ON ai_agent_action_draft (action_id);
CREATE INDEX idx_ai_agent_action_draft_customer ON ai_agent_action_draft (tenant_id, customer_id, status, created_at);
CREATE INDEX idx_ai_agent_action_draft_trace ON ai_agent_action_draft (trace_id);
