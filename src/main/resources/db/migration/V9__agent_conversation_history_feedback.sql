CREATE TABLE IF NOT EXISTS ai_agent_conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    roles VARCHAR(512),
    last_message LONGTEXT,
    last_trace_id VARCHAR(128),
    last_risk_level VARCHAR(32),
    message_count INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_agent_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    message_id VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL,
    content LONGTEXT NOT NULL,
    trace_id VARCHAR(128),
    risk_level VARCHAR(32),
    confidence DECIMAL(8,4),
    citations_json LONGTEXT,
    tool_calls_json LONGTEXT,
    latency_ms BIGINT,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_agent_message_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    feedback_id VARCHAR(128) NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    message_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128),
    user_id VARCHAR(64) NOT NULL,
    rating VARCHAR(32) NOT NULL,
    reason VARCHAR(128),
    comment LONGTEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uq_ai_agent_conversation_tenant_conv ON ai_agent_conversation (tenant_id, conversation_id);
CREATE UNIQUE INDEX uq_ai_agent_message_tenant_msg ON ai_agent_message (tenant_id, message_id);
CREATE UNIQUE INDEX uq_ai_agent_feedback_tenant_feedback ON ai_agent_message_feedback (tenant_id, feedback_id);

CREATE INDEX idx_ai_agent_conversation_user_updated ON ai_agent_conversation (tenant_id, user_id, updated_at);
CREATE INDEX idx_ai_agent_message_conversation_time ON ai_agent_message (tenant_id, conversation_id, created_at);
CREATE INDEX idx_ai_agent_message_trace ON ai_agent_message (trace_id);
CREATE INDEX idx_ai_agent_feedback_message ON ai_agent_message_feedback (tenant_id, message_id, created_at);
