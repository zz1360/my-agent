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
public class OperationsFollowUpActionExecutor implements AgentActionExecutor {

    private final JdbcTemplate jdbcTemplate;

    public OperationsFollowUpActionExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String actionType() {
        return "OPERATIONS_FOLLOW_UP";
    }

    @Override
    public String executorName() {
        return "operations-follow-up-task-executor";
    }

    @Override
    public String targetSystem() {
        return "BUSINESS_OPS_TASK_TABLE";
    }

    @Override
    public boolean lowRisk() {
        return true;
    }

    @Override
    public ActionExecutionResult execute(AgentActionResponse action,
                                         AgentUserContext context,
                                         AgentActionExecuteRequest request) {
        String refId = "ops-task-" + action.actionId();
        boolean replay = ensureOpsTask(action, context, refId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("externalRefId", refId);
        response.put("opsTaskCreated", true);
        response.put("idempotentReplay", replay);
        response.put("taskOwnerRole", "OPERATIONS");
        response.put("customerId", action.customerId());
        response.put("comment", request.comment());
        return new ActionExecutionResult(targetSystem(), refId, response);
    }

    private boolean ensureOpsTask(AgentActionResponse action, AgentUserContext context, String taskId) {
        String existing = jdbcTemplate.query("""
                SELECT task_id FROM logistics_ops_task
                WHERE tenant_id = ? AND action_id = ?
                LIMIT 1
                """, rs -> rs.next() ? rs.getString("task_id") : null, context.tenantId(), action.actionId());
        if (existing != null) {
            return true;
        }
        jdbcTemplate.update("""
                INSERT INTO logistics_ops_task
                (tenant_id, task_id, action_id, customer_id, title, description, owner_role, status, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, context.tenantId(), taskId, action.actionId(), action.customerId(), action.title(),
                action.draftContent(), "OPERATIONS", "OPEN", context.userId(), Timestamp.from(Instant.now()));
        return false;
    }
}
