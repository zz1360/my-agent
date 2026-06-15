CREATE TABLE ai_eval_release_gate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    gate_id VARCHAR(128) NOT NULL,
    suite_id VARCHAR(128) NOT NULL,
    candidate_run_id VARCHAR(128) NOT NULL,
    baseline_run_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    total_cases INT NOT NULL,
    passed_cases INT NOT NULL,
    failed_cases INT NOT NULL,
    pass_rate DECIMAL(10, 4) NOT NULL,
    min_pass_rate DECIMAL(10, 4) NOT NULL,
    regressed_cases INT NOT NULL,
    max_regressions INT NOT NULL,
    reasons_json LONGTEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX ux_ai_eval_release_gate_tenant_gate
    ON ai_eval_release_gate (tenant_id, gate_id);

CREATE INDEX idx_ai_eval_release_gate_suite_status
    ON ai_eval_release_gate (tenant_id, suite_id, status, created_at);

UPDATE ai_eval_suite
SET suite_version = 'v2.0',
    description = '覆盖核心问答、客户诊断、提示词注入、RAG 检索质量、质量治理闭环、告警任务流转、评测版本对比与企业发布门禁的默认回归套件。',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'T001' AND suite_id = 'suite-logistics-regression';
