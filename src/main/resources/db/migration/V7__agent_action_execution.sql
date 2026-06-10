CREATE TABLE IF NOT EXISTS ai_agent_action_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    execution_id VARCHAR(128) NOT NULL,
    action_id VARCHAR(128) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    executor_name VARCHAR(128) NOT NULL,
    target_system VARCHAR(128) NOT NULL,
    low_risk TINYINT(1) NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_json LONGTEXT,
    response_json LONGTEXT,
    failure_reason LONGTEXT,
    retry_count INT NOT NULL,
    executed_by VARCHAR(64) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP
);

CREATE UNIQUE INDEX uq_ai_agent_action_execution_id ON ai_agent_action_execution (execution_id);
CREATE INDEX idx_ai_agent_action_execution_action ON ai_agent_action_execution (tenant_id, action_id, started_at);
CREATE INDEX idx_ai_agent_action_execution_status ON ai_agent_action_execution (tenant_id, status, started_at);
