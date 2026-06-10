package com.superagent.logistics.api;

import com.superagent.logistics.action.AgentActionService;
import com.superagent.logistics.api.dto.AgentActionGenerateRequest;
import com.superagent.logistics.api.dto.AgentActionResponse;
import com.superagent.logistics.api.dto.AgentActionReviewRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent/actions")
public class AgentActionController {

    private final AgentActionService actionService;

    public AgentActionController(AgentActionService actionService) {
        this.actionService = actionService;
    }

    @PostMapping("/from-diagnosis")
    public List<AgentActionResponse> generateFromDiagnosis(@Valid @RequestBody AgentActionGenerateRequest request) {
        return actionService.generateFromDiagnosis(request);
    }

    @GetMapping
    public List<AgentActionResponse> list(@RequestParam(required = false) String tenantId,
                                          @RequestParam(required = false) String userId,
                                          @RequestParam(required = false) List<String> roles,
                                          @RequestParam(required = false) String customerId,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "20") int limit) {
        return actionService.list(tenantId, userId, roles, customerId, status, limit);
    }

    @GetMapping("/{actionId}")
    public AgentActionResponse get(@PathVariable String actionId,
                                   @RequestParam(required = false) String tenantId,
                                   @RequestParam(required = false) String userId,
                                   @RequestParam(required = false) List<String> roles) {
        return actionService.get(tenantId, userId, roles, actionId);
    }

    @PostMapping("/{actionId}/review")
    public AgentActionResponse review(@PathVariable String actionId,
                                      @Valid @RequestBody AgentActionReviewRequest request) {
        return actionService.review(actionId, request);
    }
}
