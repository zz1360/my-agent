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
public class CustomerReplyDraftActionExecutor implements AgentActionExecutor {

    private final JdbcTemplate jdbcTemplate;

    public CustomerReplyDraftActionExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String actionType() {
        return "CUSTOMER_REPLY";
    }

    @Override
    public String executorName() {
        return "customer-reply-draft-executor";
    }

    @Override
    public String targetSystem() {
        return "BUSINESS_CUSTOMER_REPLY_DRAFT_TABLE";
    }

    @Override
    public boolean lowRisk() {
        return false;
    }

    @Override
    public ActionExecutionResult execute(AgentActionResponse action,
                                         AgentUserContext context,
                                         AgentActionExecuteRequest request) {
        String refId = "reply-draft-" + action.actionId();
        boolean replay = ensureReplyDraft(action, context, refId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("externalRefId", refId);
        response.put("replyDraftSaved", true);
        response.put("idempotentReplay", replay);
        response.put("messageSent", false);
        response.put("manualSendRequired", true);
        response.put("customerId", action.customerId());
        response.put("comment", request.comment());
        return new ActionExecutionResult(targetSystem(), refId, response);
    }

    private boolean ensureReplyDraft(AgentActionResponse action, AgentUserContext context, String replyId) {
        String existing = jdbcTemplate.query("""
                SELECT reply_id FROM logistics_customer_reply_draft
                WHERE tenant_id = ? AND action_id = ?
                LIMIT 1
                """, rs -> rs.next() ? rs.getString("reply_id") : null, context.tenantId(), action.actionId());
        if (existing != null) {
            return true;
        }
        jdbcTemplate.update("""
                INSERT INTO logistics_customer_reply_draft
                (tenant_id, reply_id, action_id, customer_id, content, status, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, context.tenantId(), replyId, action.actionId(), action.customerId(), action.draftContent(),
                "DRAFT", context.userId(), Timestamp.from(Instant.now()));
        return false;
    }
}
