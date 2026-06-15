package com.superagent.logistics.api;

import com.superagent.logistics.api.dto.EvalCaseResponse;
import com.superagent.logistics.api.dto.EvalReleaseGateRequest;
import com.superagent.logistics.api.dto.EvalReleaseGateResponse;
import com.superagent.logistics.api.dto.EvalRunComparisonResponse;
import com.superagent.logistics.api.dto.EvalRunResponse;
import com.superagent.logistics.api.dto.EvalSuiteResponse;
import com.superagent.logistics.eval.AgentEvalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent/evals")
public class AgentEvalController {

    private final AgentEvalService evalService;

    public AgentEvalController(AgentEvalService evalService) {
        this.evalService = evalService;
    }

    @GetMapping("/cases")
    public List<EvalCaseResponse> listCases(@RequestParam(required = false) String tenantId,
                                            @RequestParam(defaultValue = "true") boolean enabledOnly) {
        return evalService.listCases(tenantId, enabledOnly);
    }

    @GetMapping("/suites")
    public List<EvalSuiteResponse> listSuites(@RequestParam(required = false) String tenantId,
                                              @RequestParam(defaultValue = "true") boolean enabledOnly) {
        return evalService.listSuites(tenantId, enabledOnly);
    }

    @GetMapping("/runs")
    public List<EvalRunResponse> listRuns(@RequestParam(required = false) String tenantId,
                                          @RequestParam(defaultValue = "10") int limit) {
        return evalService.listRuns(tenantId, limit);
    }

    @GetMapping("/runs/compare")
    public EvalRunComparisonResponse compareRuns(@RequestParam String baselineRunId,
                                                 @RequestParam String candidateRunId) {
        return evalService.compareRuns(baselineRunId, candidateRunId);
    }

    @GetMapping("/release-gates")
    public List<EvalReleaseGateResponse> listReleaseGates(@RequestParam(required = false) String tenantId,
                                                          @RequestParam(required = false) String suiteId,
                                                          @RequestParam(defaultValue = "10") int limit) {
        return evalService.listReleaseGates(tenantId, suiteId, limit);
    }

    @PostMapping("/run")
    public EvalRunResponse run(@RequestParam(required = false) String tenantId,
                               @RequestParam(required = false) String modelVersion,
                               @RequestParam(required = false) String knowledgeVersion,
                               @RequestParam(required = false) String promptVersion) {
        return evalService.run(tenantId, modelVersion, knowledgeVersion, promptVersion);
    }

    @PostMapping("/suites/{suiteId}/run")
    public EvalRunResponse runSuite(@PathVariable String suiteId,
                                    @RequestParam(required = false) String tenantId,
                                    @RequestParam(required = false) String modelVersion,
                                    @RequestParam(required = false) String knowledgeVersion,
                                    @RequestParam(required = false) String promptVersion) {
        return evalService.runSuite(tenantId, suiteId, modelVersion, knowledgeVersion, promptVersion);
    }

    @PostMapping("/release-gates/run")
    public EvalReleaseGateResponse runReleaseGate(@RequestBody(required = false) EvalReleaseGateRequest request) {
        return evalService.runReleaseGate(request);
    }

    @GetMapping("/runs/{runId}")
    public EvalRunResponse findRun(@PathVariable String runId) {
        return evalService.findRun(runId);
    }
}
