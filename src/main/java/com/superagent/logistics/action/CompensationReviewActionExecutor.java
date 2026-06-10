package com.superagent.logistics.action;

import com.superagent.logistics.api.dto.AgentActionExecuteRequest;
import com.superagent.logistics.api.dto.AgentActionResponse;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CompensationReviewActionExecutor implements AgentActionExecutor {

    private final JdbcTemplate jdbcTemplate;

    public CompensationReviewActionExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String actionType() {
        return "COMPENSATION_REVIEW";
    }

    @Override
    public String executorName() {
        return "compensation-review-queue-executor";
    }

    @Override
    public String targetSystem() {
        return "BUSINESS_COMPENSATION_REVIEW_TABLE";
    }

    @Override
    public boolean lowRisk() {
        return false;
    }

    @Override
    public ActionExecutionResult execute(AgentActionResponse action,
                                         AgentUserContext context,
                                         AgentActionExecuteRequest request) {
        String refId = "comp-review-" + action.actionId();
        boolean replay = ensureCompensationReview(action, context, refId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("externalRefId", refId);
        response.put("reviewQueueItemCreated", true);
        response.put("idempotentReplay", replay);
        response.put("paymentCreated", false);
        response.put("manualAmountRequired", true);
        response.put("customerId", action.customerId());
        response.put("waybillId", action.waybillId());
        response.put("comment", request.comment());
        return new ActionExecutionResult(targetSystem(), refId, response);
    }

    private boolean ensureCompensationReview(AgentActionResponse action, AgentUserContext context, String reviewId) {
        String existing = jdbcTemplate.query("""
                SELECT review_id FROM logistics_compensation_review_task
                WHERE tenant_id = ? AND action_id = ?
                LIMIT 1
                """, rs -> rs.next() ? rs.getString("review_id") : null, context.tenantId(), action.actionId());
        if (existing != null) {
            return true;
        }
        jdbcTemplate.update("""
                INSERT INTO logistics_compensation_review_task
                (tenant_id, review_id, action_id, customer_id, waybill_id, review_content,
                 payment_created, status, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, context.tenantId(), reviewId, action.actionId(), action.customerId(), action.waybillId(),
                action.draftContent(), false, "WAITING_REVIEW", context.userId(), Timestamp.from(Instant.now()));
        return false;
    }
}
