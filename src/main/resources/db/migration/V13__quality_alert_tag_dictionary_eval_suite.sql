CREATE TABLE IF NOT EXISTS ai_feedback_tag_dictionary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    tag_code VARCHAR(64) NOT NULL,
    tag_name VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    description VARCHAR(512),
    enabled TINYINT(1) NOT NULL,
    sort_order INT NOT NULL DEFAULT 100,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uq_ai_feedback_tag_dictionary_code
    ON ai_feedback_tag_dictionary (tenant_id, tag_code);

CREATE INDEX idx_ai_feedback_tag_dictionary_enabled
    ON ai_feedback_tag_dictionary (tenant_id, enabled, sort_order);

CREATE TABLE IF NOT EXISTS ai_quality_alert_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    rule_id VARCHAR(128) NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    metric_type VARCHAR(64) NOT NULL,
    threshold_value DECIMAL(12,4) NOT NULL,
    window_days INT NOT NULL DEFAULT 7,
    severity VARCHAR(32) NOT NULL,
    enabled TINYINT(1) NOT NULL,
    description VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uq_ai_quality_alert_rule_id
    ON ai_quality_alert_rule (tenant_id, rule_id);

CREATE INDEX idx_ai_quality_alert_rule_enabled
    ON ai_quality_alert_rule (tenant_id, enabled, metric_type);

CREATE TABLE IF NOT EXISTS ai_quality_alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    alert_id VARCHAR(128) NOT NULL,
    rule_id VARCHAR(128) NOT NULL,
    metric_type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    metric_value DECIMAL(12,4) NOT NULL,
    threshold_value DECIMAL(12,4) NOT NULL,
    window_days INT NOT NULL,
    summary VARCHAR(512) NOT NULL,
    detail_json LONGTEXT,
    first_triggered_at TIMESTAMP NOT NULL,
    last_triggered_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP
);

CREATE UNIQUE INDEX uq_ai_quality_alert_id
    ON ai_quality_alert (tenant_id, alert_id);

CREATE INDEX idx_ai_quality_alert_status
    ON ai_quality_alert (tenant_id, status, last_triggered_at);

CREATE TABLE IF NOT EXISTS ai_eval_suite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    suite_id VARCHAR(128) NOT NULL,
    suite_name VARCHAR(255) NOT NULL,
    suite_version VARCHAR(64) NOT NULL,
    description VARCHAR(512),
    enabled TINYINT(1) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uq_ai_eval_suite_id
    ON ai_eval_suite (tenant_id, suite_id);

CREATE INDEX idx_ai_eval_suite_enabled
    ON ai_eval_suite (tenant_id, enabled, updated_at);

CREATE TABLE IF NOT EXISTS ai_eval_suite_case (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    suite_id VARCHAR(128) NOT NULL,
    case_id VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 100,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uq_ai_eval_suite_case
    ON ai_eval_suite_case (tenant_id, suite_id, case_id);

CREATE INDEX idx_ai_eval_suite_case_suite
    ON ai_eval_suite_case (tenant_id, suite_id, sort_order);

ALTER TABLE ai_eval_run
    ADD COLUMN suite_id VARCHAR(128);

ALTER TABLE ai_eval_run
    ADD COLUMN suite_version VARCHAR(64);

INSERT INTO ai_feedback_tag_dictionary
(tenant_id, tag_code, tag_name, category, description, enabled, sort_order, created_at, updated_at)
VALUES
('T001', 'RAG_QUALITY', '检索或引用质量', 'RAG', '召回、精排、引用覆盖或引用可信度问题', 1, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('T001', 'POLICY_GAP', '制度知识缺口', 'KNOWLEDGE', '知识库缺少、过期或制度覆盖不足', 1, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('T001', 'TOOL_OR_DATA', '工具或业务数据', 'TOOL', '业务查询工具、业务库数据完整性或字段口径问题', 1, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('T001', 'ACTION_SAFETY', '动作安全', 'ACTION', '自动化动作、审批、幂等或执行安全问题', 1, 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('T001', 'ANSWER_QUALITY', '回答质量', 'ANSWER', '表达、结构、推理或业务建议质量问题', 1, 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO ai_quality_alert_rule
(tenant_id, rule_id, rule_name, metric_type, threshold_value, window_days, severity, enabled, description, created_at, updated_at)
VALUES
('T001', 'qa-negative-rate-7d', '近 7 天负反馈率过高', 'NEGATIVE_RATE', 0.2000, 7, 'WARN', 1, '近 7 天 NOT_HELPFUL / 总反馈数 超过阈值时触发', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('T001', 'qa-review-backlog', '评测候选审批积压', 'REVIEW_BACKLOG', 5.0000, 30, 'WARN', 1, 'UNREVIEWED 或 REVIEWING 候选数量超过阈值时触发', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('T001', 'qa-rag-failure-rate-7d', '近 7 天 RAG 实验失败率过高', 'RAG_FAILURE_RATE', 0.3000, 7, 'ERROR', 1, '近 7 天 RAG 实验 FAILED / 总运行数 超过阈值时触发', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
