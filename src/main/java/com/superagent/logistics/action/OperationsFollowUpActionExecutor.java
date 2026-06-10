package com.superagent.logistics.action;

import com.superagent.logistics.api.dto.AgentActionExecuteRequest;
import com.superagent.logistics.api.dto.AgentActionResponse;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OperationsFollowUpActionExecutor implements AgentActionExecutor {

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
        return "SIMULATED_OPS_TASK_CENTER";
    }

    @Override
    public boolean lowRisk() {
        return true;
    }

    @Override
    public ActionExecutionResult execute(AgentActionResponse action,
                                         AgentUserContext context,
                                         AgentActionExecuteRequest request) {
        String refId = "ops-follow-up-" + action.actionId();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("externalRefId", refId);
        response.put("opsTaskCreated", true);
        response.put("taskOwnerRole", "OPERATIONS");
        response.put("customerId", action.customerId());
        response.put("comment", request.comment());
        return new ActionExecutionResult(targetSystem(), refId, response);
    }
}
