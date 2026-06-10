package com.superagent.logistics.api;

import com.superagent.logistics.api.dto.RagExperimentRequest;
import com.superagent.logistics.api.dto.RagExperimentResponse;
import com.superagent.logistics.api.dto.RagExperimentRunResponse;
import com.superagent.logistics.knowledge.RagExperimentService;
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
@RequestMapping("/api/rag/experiments")
public class RagExperimentController {

    private final RagExperimentService ragExperimentService;

    public RagExperimentController(RagExperimentService ragExperimentService) {
        this.ragExperimentService = ragExperimentService;
    }

    @GetMapping
    public List<RagExperimentResponse> list(@RequestParam(required = false) String tenantId,
                                            @RequestParam(required = false) String userId,
                                            @RequestParam(required = false) List<String> roles,
                                            @RequestParam(defaultValue = "true") boolean enabledOnly) {
        return ragExperimentService.list(tenantId, userId, roles, enabledOnly);
    }

    @PostMapping
    public RagExperimentResponse upsert(@Valid @RequestBody RagExperimentRequest request) {
        return ragExperimentService.upsert(request);
    }

    @GetMapping("/{experimentId}")
    public RagExperimentResponse get(@PathVariable String experimentId,
                                     @RequestParam(required = false) String tenantId,
                                     @RequestParam(required = false) String userId,
                                     @RequestParam(required = false) List<String> roles) {
        return ragExperimentService.get(tenantId, userId, roles, experimentId);
    }

    @PostMapping("/{experimentId}/run")
    public List<RagExperimentRunResponse> run(@PathVariable String experimentId,
                                              @RequestParam(required = false) String tenantId,
                                              @RequestParam(required = false) String userId,
                                              @RequestParam(required = false) List<String> roles,
                                              @RequestParam(required = false) List<String> modes) {
        return ragExperimentService.runExperiment(tenantId, userId, roles, experimentId, modes);
    }

    @GetMapping("/{experimentId}/runs")
    public List<RagExperimentRunResponse> listRuns(@PathVariable String experimentId,
                                                   @RequestParam(required = false) String tenantId,
                                                   @RequestParam(required = false) String userId,
                                                   @RequestParam(required = false) List<String> roles,
                                                   @RequestParam(defaultValue = "20") int limit) {
        return ragExperimentService.listRuns(tenantId, userId, roles, experimentId, limit);
    }
}
