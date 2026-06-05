CREATE TABLE IF NOT EXISTS logistics_customer (
    tenant_id VARCHAR(64) NOT NULL,
    customer_id VARCHAR(32) NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    industry VARCHAR(64) NOT NULL,
    customer_level VARCHAR(32) NOT NULL,
    service_owner VARCHAR(64) NOT NULL,
    sales_owner VARCHAR(64) NOT NULL,
    contact_name VARCHAR(64) NOT NULL,
    contact_phone VARCHAR(32) NOT NULL,
    region VARCHAR(64) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    monthly_volume INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    PRIMARY KEY (tenant_id, customer_id)
);

CREATE TABLE IF NOT EXISTS logistics_sla (
    tenant_id VARCHAR(64) NOT NULL,
    sla_id VARCHAR(64) NOT NULL,
    customer_level VARCHAR(32) NOT NULL,
    service_type VARCHAR(64) NOT NULL,
    promise_hours INT NOT NULL,
    delay_compensation_rule VARCHAR(512) NOT NULL,
    temp_range VARCHAR(64),
    notes VARCHAR(512),
    PRIMARY KEY (tenant_id, sla_id)
);

CREATE TABLE IF NOT EXISTS logistics_waybill (
    tenant_id VARCHAR(64) NOT NULL,
    waybill_id VARCHAR(64) NOT NULL,
    customer_id VARCHAR(32) NOT NULL,
    origin_city VARCHAR(64) NOT NULL,
    dest_city VARCHAR(64) NOT NULL,
    service_type VARCHAR(64) NOT NULL,
    cargo_type VARCHAR(64) NOT NULL,
    weight_kg DECIMAL(12,2) NOT NULL,
    volume_m3 DECIMAL(12,2) NOT NULL,
    order_date DATE NOT NULL,
    promised_delivery_time TIMESTAMP NOT NULL,
    actual_delivery_time TIMESTAMP,
    status VARCHAR(32) NOT NULL,
    freight_fee DECIMAL(12,2) NOT NULL,
    route_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (tenant_id, waybill_id)
);

CREATE TABLE IF NOT EXISTS logistics_tracking_event (
    tenant_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    waybill_id VARCHAR(64) NOT NULL,
    event_time TIMESTAMP NOT NULL,
    node_name VARCHAR(128) NOT NULL,
    city VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    description VARCHAR(512) NOT NULL,
    PRIMARY KEY (tenant_id, event_id)
);

CREATE TABLE IF NOT EXISTS logistics_exception_event (
    tenant_id VARCHAR(64) NOT NULL,
    exception_id VARCHAR(64) NOT NULL,
    waybill_id VARCHAR(64) NOT NULL,
    customer_id VARCHAR(32) NOT NULL,
    event_time TIMESTAMP NOT NULL,
    exception_type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    responsibility_party VARCHAR(64) NOT NULL,
    description VARCHAR(512) NOT NULL,
    resolved TINYINT(1) NOT NULL,
    impact_hours INT NOT NULL,
    PRIMARY KEY (tenant_id, exception_id)
);

CREATE TABLE IF NOT EXISTS logistics_ticket (
    tenant_id VARCHAR(64) NOT NULL,
    ticket_id VARCHAR(64) NOT NULL,
    customer_id VARCHAR(32) NOT NULL,
    waybill_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    ticket_type VARCHAR(64) NOT NULL,
    priority VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    owner_team VARCHAR(64) NOT NULL,
    summary VARCHAR(512) NOT NULL,
    resolution VARCHAR(512),
    compensation_amount DECIMAL(12,2),
    PRIMARY KEY (tenant_id, ticket_id)
);

CREATE TABLE IF NOT EXISTS ai_knowledge_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    doc_id VARCHAR(128) NOT NULL,
    title VARCHAR(255) NOT NULL,
    doc_type VARCHAR(64) NOT NULL,
    biz_domain VARCHAR(64) NOT NULL,
    version VARCHAR(64),
    source_url VARCHAR(512),
    acl_roles VARCHAR(512),
    effective_from DATE,
    effective_to DATE,
    status VARCHAR(32) NOT NULL,
    content LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_knowledge_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    doc_id VARCHAR(128) NOT NULL,
    chunk_id VARCHAR(128) NOT NULL,
    title_path VARCHAR(512) NOT NULL,
    content LONGTEXT NOT NULL,
    metadata LONGTEXT,
    acl_roles VARCHAR(512),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_agent_trace (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(128),
    user_message LONGTEXT NOT NULL,
    final_answer LONGTEXT,
    model_name VARCHAR(128),
    risk_level VARCHAR(32),
    latency_ms BIGINT,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_agent_tool_call (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id VARCHAR(128) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    arguments_json LONGTEXT,
    result_summary LONGTEXT,
    status VARCHAR(32) NOT NULL,
    latency_ms BIGINT,
    error_code VARCHAR(64),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_eval_case (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    case_id VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    endpoint VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    roles VARCHAR(512) NOT NULL,
    request_json LONGTEXT NOT NULL,
    expected_contains LONGTEXT,
    expected_citations LONGTEXT,
    expected_min_tool_calls INT NOT NULL,
    risk_level VARCHAR(32),
    enabled TINYINT(1) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_eval_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_cases INT NOT NULL,
    passed_cases INT NOT NULL,
    failed_cases INT NOT NULL,
    model_provider VARCHAR(128),
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_eval_case_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(128) NOT NULL,
    case_id VARCHAR(128) NOT NULL,
    passed TINYINT(1) NOT NULL,
    trace_id VARCHAR(128),
    risk_level VARCHAR(32),
    latency_ms BIGINT,
    failure_reason LONGTEXT,
    response_excerpt LONGTEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uq_ai_knowledge_document_tenant_doc ON ai_knowledge_document (tenant_id, doc_id);
CREATE UNIQUE INDEX uq_ai_knowledge_chunk_tenant_chunk ON ai_knowledge_chunk (tenant_id, chunk_id);
CREATE UNIQUE INDEX uq_ai_agent_trace_trace_id ON ai_agent_trace (trace_id);
CREATE UNIQUE INDEX uq_ai_eval_case_tenant_case ON ai_eval_case (tenant_id, case_id);
CREATE UNIQUE INDEX uq_ai_eval_run_run_id ON ai_eval_run (run_id);

CREATE INDEX idx_logistics_waybill_customer_date ON logistics_waybill (tenant_id, customer_id, order_date);
CREATE INDEX idx_logistics_exception_customer_time ON logistics_exception_event (tenant_id, customer_id, event_time);
CREATE INDEX idx_logistics_ticket_customer_time ON logistics_ticket (tenant_id, customer_id, created_at);
CREATE INDEX idx_logistics_tracking_waybill_time ON logistics_tracking_event (tenant_id, waybill_id, event_time);
CREATE INDEX idx_ai_knowledge_document_status ON ai_knowledge_document (tenant_id, status, doc_type, biz_domain);
CREATE INDEX idx_ai_knowledge_chunk_doc ON ai_knowledge_chunk (tenant_id, doc_id);
CREATE INDEX idx_ai_agent_trace_search ON ai_agent_trace (tenant_id, user_id, created_at);
CREATE INDEX idx_ai_agent_tool_call_trace ON ai_agent_tool_call (trace_id);
CREATE INDEX idx_ai_eval_case_enabled ON ai_eval_case (tenant_id, enabled);
CREATE INDEX idx_ai_eval_case_result_run ON ai_eval_case_result (run_id, case_id);
