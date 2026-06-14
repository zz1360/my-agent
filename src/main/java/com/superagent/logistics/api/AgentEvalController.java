package com.superagent.logistics.api;

import com.superagent.logistics.api.dto.EvalCaseResponse;
import com.superagent.logistics.api.dto.EvalRunResponse;
import com.superagent.logistics.api.dto.EvalSuiteResponse;
import com.superagent.logistics.eval.AgentEvalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/run")
    public EvalRunResponse run(@RequestParam(required = false) String tenantId) {
        return evalService.run(tenantId);
    }

    @PostMapping("/suites/{suiteId}/run")
    public EvalRunResponse runSuite(@PathVariable String suiteId,
                                    @RequestParam(required = false) String tenantId) {
        return evalService.runSuite(tenantId, suiteId);
    }

    @GetMapping("/runs/{runId}")
    public EvalRunResponse findRun(@PathVariable String runId) {
        return evalService.findRun(runId);
    }
}
