package com.superagent.logistics.action;

import com.superagent.logistics.api.dto.AgentActionExecuteRequest;
import com.superagent.logistics.api.dto.AgentActionResponse;
import com.superagent.logistics.security.AgentUserContext;

public interface AgentActionExecutor {

    String actionType();

    String executorName();

    String targetSystem();

    boolean lowRisk();

    ActionExecutionResult execute(AgentActionResponse action,
                                  AgentUserContext context,
                                  AgentActionExecuteRequest request);
}
