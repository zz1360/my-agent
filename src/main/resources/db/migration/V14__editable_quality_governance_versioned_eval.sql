ALTER TABLE ai_quality_alert
    ADD COLUMN task_id VARCHAR(128);

ALTER TABLE ai_quality_alert
    ADD COLUMN task_created_at TIMESTAMP;

ALTER TABLE ai_eval_run
    ADD COLUMN model_version VARCHAR(128);

ALTER TABLE ai_eval_run
    ADD COLUMN knowledge_version VARCHAR(128);

ALTER TABLE ai_eval_run
    ADD COLUMN prompt_version VARCHAR(128);

CREATE INDEX idx_ai_quality_alert_task
    ON ai_quality_alert (tenant_id, task_id);

UPDATE ai_eval_suite
SET suite_version = 'v1.8',
    description = '覆盖核心问答、客户诊断、提示词注入、RAG 检索质量与质量治理闭环的默认回归套件。',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'T001' AND suite_id = 'suite-logistics-regression';
