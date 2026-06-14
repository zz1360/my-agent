ALTER TABLE logistics_ops_task
    ADD COLUMN owner_user_id VARCHAR(64);

ALTER TABLE logistics_ops_task
    ADD COLUMN last_comment VARCHAR(1024);

ALTER TABLE logistics_ops_task
    ADD COLUMN updated_at TIMESTAMP;

UPDATE logistics_ops_task
SET updated_at = created_at
WHERE updated_at IS NULL;

CREATE INDEX idx_logistics_ops_task_quality_flow
    ON logistics_ops_task (tenant_id, customer_id, status, updated_at);

UPDATE ai_eval_suite
SET suite_version = 'v1.9',
    description = '覆盖核心问答、客户诊断、提示词注入、RAG 检索质量、质量治理闭环、告警任务流转与评测版本对比的默认回归套件。',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'T001' AND suite_id = 'suite-logistics-regression';
