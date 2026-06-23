package com.superagent.logistics.api;

import com.superagent.logistics.api.dto.FeedbackTagUpsertRequest;
import com.superagent.logistics.api.dto.FeedbackTagResponse;
import com.superagent.logistics.api.dto.QualityAlertEvaluationResponse;
import com.superagent.logistics.api.dto.QualityAlertResponse;
import com.superagent.logistics.api.dto.QualityAlertRuleUpsertRequest;
import com.superagent.logistics.api.dto.QualityAlertRuleResponse;
import com.superagent.logistics.api.dto.QualityAlertTaskDetailResponse;
import com.superagent.logistics.api.dto.QualityAlertTaskResponse;
import com.superagent.logistics.api.dto.QualityAlertTaskUpdateRequest;
import com.superagent.logistics.api.dto.QualityTrendResponse;
import com.superagent.logistics.api.dto.PageResponse;
import com.superagent.logistics.eval.AgentQualityGovernanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/agent/quality")
public class AgentQualityGovernanceController {

    private final AgentQualityGovernanceService qualityGovernanceService;

    public AgentQualityGovernanceController(AgentQualityGovernanceService qualityGovernanceService) {
        this.qualityGovernanceService = qualityGovernanceService;
    }

    @GetMapping("/feedback-tags")
    public List<FeedbackTagResponse> feedbackTags(@RequestParam(required = false) String tenantId,
                                                  @RequestParam(required = false) String userId,
                                                  @RequestParam(required = false) List<String> roles,
                                                  @RequestParam(defaultValue = "true") boolean enabledOnly) {
        return qualityGovernanceService.listFeedbackTags(tenantId, userId, roles, enabledOnly);
    }

    @PostMapping("/feedback-tags/{tagCode}")
    @PreAuthorize("@agentMethodSecurity.hasPermission('QUALITY_MANAGE')")
    public FeedbackTagResponse upsertFeedbackTag(@PathVariable String tagCode,
                                                 @RequestBody FeedbackTagUpsertRequest request) {
        return qualityGovernanceService.upsertFeedbackTag(tagCode, request);
    }

    @GetMapping("/alert-rules")
    public List<QualityAlertRuleResponse> alertRules(@RequestParam(required = false) String tenantId,
                                                     @RequestParam(required = false) String userId,
                                                     @RequestParam(required = false) List<String> roles,
                                                     @RequestParam(defaultValue = "true") boolean enabledOnly) {
        return qualityGovernanceService.listAlertRules(tenantId, userId, roles, enabledOnly);
    }

    @PostMapping("/alert-rules/{ruleId}")
    @PreAuthorize("@agentMethodSecurity.hasPermission('QUALITY_MANAGE')")
    public QualityAlertRuleResponse upsertAlertRule(@PathVariable String ruleId,
                                                    @RequestBody QualityAlertRuleUpsertRequest request) {
        return qualityGovernanceService.upsertAlertRule(ruleId, request);
    }

    @GetMapping("/alerts")
    public List<QualityAlertResponse> alerts(@RequestParam(required = false) String tenantId,
                                             @RequestParam(required = false) String userId,
                                             @RequestParam(required = false) List<String> roles,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "30") int limit) {
        return qualityGovernanceService.listAlerts(tenantId, userId, roles, status, limit);
    }

    @GetMapping("/alerts/page")
    public PageResponse<QualityAlertResponse> alertsPage(@RequestParam(required = false) String tenantId,
                                                          @RequestParam(required = false) String userId,
                                                          @RequestParam(required = false) List<String> roles,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return qualityGovernanceService.pageAlerts(tenantId, userId, roles, status, page, size);
    }

    @PostMapping("/alerts/evaluate")
    @PreAuthorize("@agentMethodSecurity.hasPermission('QUALITY_MANAGE')")
    public QualityAlertEvaluationResponse evaluateAlerts(@RequestParam(required = false) String tenantId,
                                                        @RequestParam(required = false) String userId,
                                                        @RequestParam(required = false) List<String> roles) {
        return qualityGovernanceService.evaluateAlerts(tenantId, userId, roles);
    }

    @PostMapping("/alerts/{alertId}/task")
    @PreAuthorize("@agentMethodSecurity.hasPermission('QUALITY_MANAGE')")
    public QualityAlertTaskResponse createAlertTask(@PathVariable String alertId,
                                                    @RequestParam(required = false) String tenantId,
                                                    @RequestParam(required = false) String userId,
                                                    @RequestParam(required = false) List<String> roles) {
        return qualityGovernanceService.createAlertTask(alertId, tenantId, userId, roles);
    }

    @GetMapping("/alert-tasks")
    public List<QualityAlertTaskDetailResponse> alertTasks(@RequestParam(required = false) String tenantId,
                                                           @RequestParam(required = false) String userId,
                                                           @RequestParam(required = false) List<String> roles,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(defaultValue = "30") int limit) {
        return qualityGovernanceService.listAlertTasks(tenantId, userId, roles, status, limit);
    }

    @GetMapping("/alert-tasks/page")
    public PageResponse<QualityAlertTaskDetailResponse> alertTasksPage(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) List<String> roles,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return qualityGovernanceService.pageAlertTasks(tenantId, userId, roles, status, page, size);
    }

    @PostMapping("/alert-tasks/{taskId}/transition")
    @PreAuthorize("@agentMethodSecurity.hasPermission('QUALITY_MANAGE')")
    public QualityAlertTaskDetailResponse transitionAlertTask(@PathVariable String taskId,
                                                              @RequestBody QualityAlertTaskUpdateRequest request) {
        return qualityGovernanceService.transitionAlertTask(taskId, request);
    }

    @GetMapping("/trends")
    public QualityTrendResponse qualityTrends(@RequestParam(required = false) String tenantId,
                                              @RequestParam(required = false) String userId,
                                              @RequestParam(required = false) List<String> roles,
                                              @RequestParam(required = false) LocalDate from,
                                              @RequestParam(required = false) LocalDate to) {
        return qualityGovernanceService.qualityTrends(tenantId, userId, roles, from, to);
    }
}
