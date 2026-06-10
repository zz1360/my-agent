package com.superagent.logistics.action;

import com.superagent.logistics.api.dto.AgentActionExecuteRequest;
import com.superagent.logistics.api.dto.AgentActionResponse;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TicketNoteActionExecutor implements AgentActionExecutor {

    @Override
    public String actionType() {
        return "TICKET_NOTE";
    }

    @Override
    public String executorName() {
        return "ticket-note-draft-executor";
    }

    @Override
    public String targetSystem() {
        return "SIMULATED_TICKET_SYSTEM";
    }

    @Override
    public boolean lowRisk() {
        return true;
    }

    @Override
    public ActionExecutionResult execute(AgentActionResponse action,
                                         AgentUserContext context,
                                         AgentActionExecuteRequest request) {
        String refId = "ticket-note-draft-" + action.actionId();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("externalRefId", refId);
        response.put("ticketNoteDraftSaved", true);
        response.put("manualPublishRequired", true);
        response.put("customerId", action.customerId());
        response.put("waybillId", action.waybillId());
        response.put("comment", request.comment());
        return new ActionExecutionResult(targetSystem(), refId, response);
    }
}
