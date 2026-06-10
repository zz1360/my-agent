package com.superagent.logistics.action;

import com.superagent.logistics.api.dto.AgentActionExecuteRequest;
import com.superagent.logistics.api.dto.AgentActionResponse;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CompensationReviewActionExecutor implements AgentActionExecutor {

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
        return "SIMULATED_COMPENSATION_REVIEW_QUEUE";
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
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("externalRefId", refId);
        response.put("reviewQueueItemCreated", true);
        response.put("paymentCreated", false);
        response.put("manualAmountRequired", true);
        response.put("customerId", action.customerId());
        response.put("waybillId", action.waybillId());
        response.put("comment", request.comment());
        return new ActionExecutionResult(targetSystem(), refId, response);
    }
}
