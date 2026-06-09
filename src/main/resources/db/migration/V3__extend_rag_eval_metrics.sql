ALTER TABLE ai_eval_case_result
    ADD COLUMN rag_recall_at_k DECIMAL(8,4);

ALTER TABLE ai_eval_case_result
    ADD COLUMN rag_precision_at_k DECIMAL(8,4);

ALTER TABLE ai_eval_case_result
    ADD COLUMN rag_mrr DECIMAL(8,4);

ALTER TABLE ai_eval_case_result
    ADD COLUMN rag_ndcg DECIMAL(8,4);

ALTER TABLE ai_eval_case_result
    ADD COLUMN rag_expected_total INT;

ALTER TABLE ai_eval_case_result
    ADD COLUMN rag_hit_count INT;
