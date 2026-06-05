package com.superagent.logistics.api;

import com.superagent.logistics.agent.CustomerDiagnosisAgentService;
import com.superagent.logistics.api.dto.CustomerDiagnosisRequest;
import com.superagent.logistics.api.dto.CustomerDiagnosisResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class CustomerDiagnosisController {

    private final CustomerDiagnosisAgentService diagnosisAgentService;

    public CustomerDiagnosisController(CustomerDiagnosisAgentService diagnosisAgentService) {
        this.diagnosisAgentService = diagnosisAgentService;
    }

    @PostMapping("/customer-diagnosis")
    public CustomerDiagnosisResponse diagnose(@Valid @RequestBody CustomerDiagnosisRequest request) {
        return diagnosisAgentService.diagnose(request);
    }
}
