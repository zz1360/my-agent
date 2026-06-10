package com.superagent.logistics.api;

import com.superagent.logistics.action.AgentActionService;
import com.superagent.logistics.action.AgentActionExecutionService;
import com.superagent.logistics.api.dto.AgentActionAutomationRequest;
import com.superagent.logistics.api.dto.AgentActionAutomationResponse;
import com.superagent.logistics.api.dto.AgentActionBusinessLinkResponse;
import com.superagent.logistics.api.dto.AgentActionExecuteRequest;
import com.superagent.logistics.api.dto.AgentActionExecutionMetricsResponse;
import com.superagent.logistics.api.dto.AgentActionExecutionResponse;
import com.superagent.logistics.api.dto.AgentActionGenerateRequest;
import com.superagent.logistics.api.dto.AgentActionResponse;
import com.superagent.logistics.api.dto.AgentActionReviewRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/agent/actions")
public class AgentActionController {

    private final AgentActionService actionService;
    private final AgentActionExecutionService executionService;

    public AgentActionController(AgentActionService actionService,
                                 AgentActionExecutionService executionService) {
        this.actionService = actionService;
        this.executionService = executionService;
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

    @GetMapping("/executions")
    public List<AgentActionExecutionResponse> searchExecutions(@RequestParam(required = false) String tenantId,
                                                               @RequestParam(required = false) String userId,
                                                               @RequestParam(required = false) List<String> roles,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(required = false) String actionType,
                                                               @RequestParam(required = false) String executorName,
                                                               @RequestParam(required = false) String targetSystem,
                                                               @RequestParam(required = false)
                                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                               @RequestParam(required = false)
                                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                               @RequestParam(defaultValue = "50") int limit) {
        return executionService.searchExecutions(tenantId, userId, roles, status, actionType, executorName,
                targetSystem, from, to, limit);
    }

    @GetMapping("/executions/retry-queue")
    public List<AgentActionExecutionResponse> retryQueue(@RequestParam(required = false) String tenantId,
                                                         @RequestParam(required = false) String userId,
                                                         @RequestParam(required = false) List<String> roles,
                                                         @RequestParam(defaultValue = "true") boolean dueOnly,
                                                         @RequestParam(defaultValue = "50") int limit) {
        return executionService.retryQueue(tenantId, userId, roles, dueOnly, limit);
    }

    @GetMapping("/executions/metrics")
    public AgentActionExecutionMetricsResponse executionMetrics(@RequestParam(required = false) String tenantId,
                                                               @RequestParam(required = false) String userId,
                                                               @RequestParam(required = false) List<String> roles,
                                                               @RequestParam(required = false)
                                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                               @RequestParam(required = false)
                                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return executionService.metrics(tenantId, userId, roles, from, to);
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

    @PostMapping("/{actionId}/execute")
    public AgentActionExecutionResponse execute(@PathVariable String actionId,
                                                @RequestBody AgentActionExecuteRequest request) {
        return executionService.execute(actionId, request);
    }

    @GetMapping("/{actionId}/executions")
    public List<AgentActionExecutionResponse> executions(@PathVariable String actionId,
                                                         @RequestParam(required = false) String tenantId,
                                                         @RequestParam(required = false) String userId,
                                                         @RequestParam(required = false) List<String> roles) {
        return executionService.listExecutions(actionId, tenantId, userId, roles);
    }

    @GetMapping("/{actionId}/business-link")
    public AgentActionBusinessLinkResponse businessLink(@PathVariable String actionId,
                                                        @RequestParam(required = false) String tenantId,
                                                        @RequestParam(required = false) String userId,
                                                        @RequestParam(required = false) List<String> roles) {
        return executionService.businessLink(actionId, tenantId, userId, roles);
    }

    @PostMapping("/executions/{executionId}/retry")
    public AgentActionExecutionResponse retryExecution(@PathVariable String executionId,
                                                       @RequestBody AgentActionExecuteRequest request) {
        return executionService.retry(executionId, request);
    }

    @PostMapping("/automation/run")
    public AgentActionAutomationResponse runAutomation(@RequestBody AgentActionAutomationRequest request) {
        return executionService.runLowRiskAutomation(request);
    }
}
