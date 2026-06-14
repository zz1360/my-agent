ALTER TABLE ai_eval_case_candidate
    ADD COLUMN feedback_tags LONGTEXT;

ALTER TABLE ai_eval_case_candidate
    ADD COLUMN annotation_note LONGTEXT;

ALTER TABLE ai_eval_case_candidate
    ADD COLUMN review_status VARCHAR(32) NOT NULL DEFAULT 'UNREVIEWED';

ALTER TABLE ai_eval_case_candidate
    ADD COLUMN reviewer_id VARCHAR(64);

ALTER TABLE ai_eval_case_candidate
    ADD COLUMN review_comment LONGTEXT;

ALTER TABLE ai_eval_case_candidate
    ADD COLUMN reviewed_at TIMESTAMP;

ALTER TABLE ai_eval_case_candidate
    ADD COLUMN annotated_by VARCHAR(64);

ALTER TABLE ai_eval_case_candidate
    ADD COLUMN annotated_at TIMESTAMP;

CREATE INDEX idx_ai_eval_case_candidate_review ON ai_eval_case_candidate (tenant_id, review_status, updated_at);
