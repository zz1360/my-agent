package com.superagent.logistics.api;

import com.superagent.logistics.api.dto.FrontendEventRequest;
import com.superagent.logistics.observability.FrontendTelemetryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ops/frontend-events")
public class FrontendTelemetryController {

    private final FrontendTelemetryService telemetryService;

    public FrontendTelemetryController(FrontendTelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void record(@Valid @RequestBody FrontendEventRequest request) {
        telemetryService.record(request);
    }
}
