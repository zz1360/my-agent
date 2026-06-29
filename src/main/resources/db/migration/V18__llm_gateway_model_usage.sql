CREATE TABLE ai_model_price (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    currency VARCHAR(16) NOT NULL,
    input_price_per_1m_tokens DECIMAL(18,8) NOT NULL,
    output_price_per_1m_tokens DECIMAL(18,8) NOT NULL,
    enabled TINYINT(1) NOT NULL,
    effective_from TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE ai_model_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(128),
    scene VARCHAR(64) NOT NULL,
    route_key VARCHAR(128) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    streaming TINYINT(1) NOT NULL,
    prompt_tokens INT,
    completion_tokens INT,
    total_tokens INT,
    usage_estimated TINYINT(1) NOT NULL,
    input_cost DECIMAL(18,8) NOT NULL,
    output_cost DECIMAL(18,8) NOT NULL,
    total_cost DECIMAL(18,8) NOT NULL,
    currency VARCHAR(16) NOT NULL,
    latency_ms BIGINT NOT NULL,
    ttft_ms BIGINT,
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(64),
    error_message LONGTEXT,
    fallback_from VARCHAR(512),
    fallback_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_ai_model_call_trace
    ON ai_model_call_log (trace_id);

CREATE INDEX idx_ai_model_call_tenant_created
    ON ai_model_call_log (tenant_id, created_at);

CREATE INDEX idx_ai_model_call_route_model
    ON ai_model_call_log (route_key, provider, model, created_at);

CREATE INDEX idx_ai_model_price_model
    ON ai_model_price (provider, model, enabled, effective_from);

INSERT INTO ai_model_price
(provider, model, currency, input_price_per_1m_tokens, output_price_per_1m_tokens, enabled, effective_from, created_at)
VALUES
('deepseek', 'deepseek-v4-flash', 'CNY', 0.00000000, 0.00000000, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
