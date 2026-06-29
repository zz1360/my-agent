package com.superagent.logistics.api;

import com.superagent.logistics.api.dto.ModelCallLogResponse;
import com.superagent.logistics.api.dto.ModelUsageSummaryResponse;
import com.superagent.logistics.llm.ModelCallLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/agent/model-calls")
public class ModelCallController {

    private final ModelCallLogService modelCallLogService;

    public ModelCallController(ModelCallLogService modelCallLogService) {
        this.modelCallLogService = modelCallLogService;
    }

    @GetMapping
    public List<ModelCallLogResponse> list(@RequestParam(required = false) String tenantId,
                                           @RequestParam(required = false) String traceId,
                                           @RequestParam(defaultValue = "50") int limit) {
        return modelCallLogService.list(tenantId, traceId, limit);
    }

    @GetMapping("/summary")
    public ModelUsageSummaryResponse summary(@RequestParam(required = false) String tenantId,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return modelCallLogService.summary(tenantId, from, to);
    }
}
