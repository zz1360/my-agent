ALTER TABLE ai_agent_action_execution ADD COLUMN external_ref_id VARCHAR(128);
ALTER TABLE ai_agent_action_execution ADD COLUMN idempotency_key VARCHAR(160);
ALTER TABLE ai_agent_action_execution ADD COLUMN next_retry_at TIMESTAMP;
ALTER TABLE ai_agent_action_execution ADD COLUMN max_retry_count INT NOT NULL DEFAULT 3;

CREATE INDEX idx_ai_agent_action_execution_idempotency ON ai_agent_action_execution (tenant_id, idempotency_key);

CREATE TABLE IF NOT EXISTS logistics_ticket_note (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    note_id VARCHAR(128) NOT NULL,
    action_id VARCHAR(128) NOT NULL,
    customer_id VARCHAR(32) NOT NULL,
    waybill_id VARCHAR(64),
    content LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS logistics_ops_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    action_id VARCHAR(128) NOT NULL,
    customer_id VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description LONGTEXT NOT NULL,
    owner_role VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS logistics_customer_reply_draft (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    reply_id VARCHAR(128) NOT NULL,
    action_id VARCHAR(128) NOT NULL,
    customer_id VARCHAR(32) NOT NULL,
    content LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS logistics_compensation_review_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    review_id VARCHAR(128) NOT NULL,
    action_id VARCHAR(128) NOT NULL,
    customer_id VARCHAR(32) NOT NULL,
    waybill_id VARCHAR(64),
    review_content LONGTEXT NOT NULL,
    payment_created TINYINT(1) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP
);

CREATE UNIQUE INDEX uq_logistics_ticket_note_action ON logistics_ticket_note (tenant_id, action_id);
CREATE UNIQUE INDEX uq_logistics_ticket_note_id ON logistics_ticket_note (tenant_id, note_id);
CREATE UNIQUE INDEX uq_logistics_ops_task_action ON logistics_ops_task (tenant_id, action_id);
CREATE UNIQUE INDEX uq_logistics_ops_task_id ON logistics_ops_task (tenant_id, task_id);
CREATE UNIQUE INDEX uq_logistics_reply_draft_action ON logistics_customer_reply_draft (tenant_id, action_id);
CREATE UNIQUE INDEX uq_logistics_reply_draft_id ON logistics_customer_reply_draft (tenant_id, reply_id);
CREATE UNIQUE INDEX uq_logistics_comp_review_action ON logistics_compensation_review_task (tenant_id, action_id);
CREATE UNIQUE INDEX uq_logistics_comp_review_id ON logistics_compensation_review_task (tenant_id, review_id);
