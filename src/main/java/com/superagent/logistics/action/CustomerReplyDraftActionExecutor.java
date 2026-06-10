package com.superagent.logistics.action;

import com.superagent.logistics.api.dto.AgentActionExecuteRequest;
import com.superagent.logistics.api.dto.AgentActionResponse;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CustomerReplyDraftActionExecutor implements AgentActionExecutor {

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
        return "SIMULATED_CRM_REPLY_DRAFT";
    }

    @Override
    public boolean lowRisk() {
        return false;
    }

    @Override
    public ActionExecutionResult execute(AgentActionResponse action,
                                         AgentUserContext context,
                                         AgentActionExecuteRequest request) {
        String refId = "crm-reply-draft-" + action.actionId();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("externalRefId", refId);
        response.put("replyDraftSaved", true);
        response.put("messageSent", false);
        response.put("manualSendRequired", true);
        response.put("customerId", action.customerId());
        response.put("comment", request.comment());
        return new ActionExecutionResult(targetSystem(), refId, response);
    }
}
