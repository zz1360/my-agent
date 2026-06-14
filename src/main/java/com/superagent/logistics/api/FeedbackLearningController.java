package com.superagent.logistics.api;

import com.superagent.logistics.api.dto.AgentFeedbackSampleResponse;
import com.superagent.logistics.api.dto.EvalCaseCandidateAnnotateRequest;
import com.superagent.logistics.api.dto.EvalCaseCandidateCreateRequest;
import com.superagent.logistics.api.dto.EvalCaseCandidatePromoteRequest;
import com.superagent.logistics.api.dto.EvalCaseCandidateReviewRequest;
import com.superagent.logistics.api.dto.EvalCaseCandidateResponse;
import com.superagent.logistics.api.dto.FeedbackCandidateAuditResponse;
import com.superagent.logistics.api.dto.FeedbackQualityMetricsResponse;
import com.superagent.logistics.api.dto.FeedbackRagExperimentResponse;
import com.superagent.logistics.eval.FeedbackLearningService;
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
@RequestMapping("/api/agent")
public class FeedbackLearningController {

    private final FeedbackLearningService feedbackLearningService;

    public FeedbackLearningController(FeedbackLearningService feedbackLearningService) {
        this.feedbackLearningService = feedbackLearningService;
    }

    @GetMapping("/feedback")
    public List<AgentFeedbackSampleResponse> feedback(@RequestParam(required = false) String tenantId,
                                                      @RequestParam(required = false) String userId,
                                                      @RequestParam(required = false) List<String> roles,
                                                      @RequestParam(defaultValue = "NOT_HELPFUL") String rating,
                                                      @RequestParam(required = false) String reason,
                                                      @RequestParam(defaultValue = "false") boolean unconvertedOnly,
                                                      @RequestParam(defaultValue = "30") int limit) {
        return feedbackLearningService.listFeedback(tenantId, userId, roles, rating, reason, unconvertedOnly, limit);
    }

    @GetMapping("/eval-candidates")
    public List<EvalCaseCandidateResponse> evalCandidates(@RequestParam(required = false) String tenantId,
                                                          @RequestParam(required = false) String userId,
                                                          @RequestParam(required = false) List<String> roles,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "30") int limit) {
        return feedbackLearningService.listCandidates(tenantId, userId, roles, status, limit);
    }

    @GetMapping("/feedback/quality-metrics")
    public FeedbackQualityMetricsResponse qualityMetrics(@RequestParam(required = false) String tenantId,
                                                         @RequestParam(required = false) String userId,
                                                         @RequestParam(required = false) List<String> roles,
                                                         @RequestParam(required = false) LocalDate from,
                                                         @RequestParam(required = false) LocalDate to) {
        return feedbackLearningService.qualityMetrics(tenantId, userId, roles, from, to);
    }

    @GetMapping("/eval-candidate-audits")
    public List<FeedbackCandidateAuditResponse> evalCandidateAudits(@RequestParam(required = false) String tenantId,
                                                                    @RequestParam(required = false) String userId,
                                                                    @RequestParam(required = false) List<String> roles,
                                                                    @RequestParam(required = false) String candidateId,
                                                                    @RequestParam(required = false) String actionType,
                                                                    @RequestParam(defaultValue = "30") int limit) {
        return feedbackLearningService.listCandidateAudits(tenantId, userId, roles, candidateId, actionType, limit);
    }

    @PostMapping("/feedback/{feedbackId}/eval-candidate")
    public EvalCaseCandidateResponse createCandidate(@PathVariable String feedbackId,
                                                     @RequestBody EvalCaseCandidateCreateRequest request) {
        return feedbackLearningService.createCandidate(feedbackId, request);
    }

    @PostMapping("/eval-candidates/{candidateId}/promote")
    public EvalCaseCandidateResponse promoteCandidate(@PathVariable String candidateId,
                                                      @RequestBody EvalCaseCandidatePromoteRequest request) {
        return feedbackLearningService.promoteToEvalCase(candidateId, request);
    }

    @PostMapping("/eval-candidates/{candidateId}/annotate")
    public EvalCaseCandidateResponse annotateCandidate(@PathVariable String candidateId,
                                                       @RequestBody EvalCaseCandidateAnnotateRequest request) {
        return feedbackLearningService.annotateCandidate(candidateId, request);
    }

    @PostMapping("/eval-candidates/{candidateId}/review")
    public EvalCaseCandidateResponse reviewCandidate(@PathVariable String candidateId,
                                                     @RequestBody EvalCaseCandidateReviewRequest request) {
        return feedbackLearningService.reviewCandidate(candidateId, request);
    }

    @PostMapping("/eval-candidates/{candidateId}/rag-experiment")
    public FeedbackRagExperimentResponse createRagExperiment(@PathVariable String candidateId,
                                                             @RequestParam(required = false) String tenantId,
                                                             @RequestParam(required = false) String userId,
                                                             @RequestParam(required = false) List<String> roles,
                                                             @RequestParam(defaultValue = "true") boolean runNow) {
        return feedbackLearningService.createRagExperiment(candidateId, tenantId, userId, roles, runNow);
    }
}
