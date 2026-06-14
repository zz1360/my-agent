package com.superagent.logistics.api;

import com.superagent.logistics.api.dto.FeedbackTagResponse;
import com.superagent.logistics.api.dto.QualityAlertEvaluationResponse;
import com.superagent.logistics.api.dto.QualityAlertResponse;
import com.superagent.logistics.api.dto.QualityAlertRuleResponse;
import com.superagent.logistics.eval.AgentQualityGovernanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/alert-rules")
    public List<QualityAlertRuleResponse> alertRules(@RequestParam(required = false) String tenantId,
                                                     @RequestParam(required = false) String userId,
                                                     @RequestParam(required = false) List<String> roles,
                                                     @RequestParam(defaultValue = "true") boolean enabledOnly) {
        return qualityGovernanceService.listAlertRules(tenantId, userId, roles, enabledOnly);
    }

    @GetMapping("/alerts")
    public List<QualityAlertResponse> alerts(@RequestParam(required = false) String tenantId,
                                             @RequestParam(required = false) String userId,
                                             @RequestParam(required = false) List<String> roles,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "30") int limit) {
        return qualityGovernanceService.listAlerts(tenantId, userId, roles, status, limit);
    }

    @PostMapping("/alerts/evaluate")
    public QualityAlertEvaluationResponse evaluateAlerts(@RequestParam(required = false) String tenantId,
                                                        @RequestParam(required = false) String userId,
                                                        @RequestParam(required = false) List<String> roles) {
        return qualityGovernanceService.evaluateAlerts(tenantId, userId, roles);
    }
}
