package com.superagent.logistics.api;

import com.superagent.logistics.api.dto.AuditResponse;
import com.superagent.logistics.audit.AgentAuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/agent/audit")
public class AgentAuditController {

    private final AgentAuditService auditService;

    public AgentAuditController(AgentAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/{traceId}")
    public ResponseEntity<AuditResponse> findByTraceId(@PathVariable String traceId) {
        return auditService.findByTraceId(traceId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<AuditResponse> search(@RequestParam(required = false) String tenantId,
                                      @RequestParam(required = false) String userId,
                                      @RequestParam(required = false) String customerId,
                                      @RequestParam(required = false) LocalDate from,
                                      @RequestParam(required = false) LocalDate to,
                                      @RequestParam(defaultValue = "20") int limit) {
        return auditService.search(tenantId, userId, customerId, from, to, limit);
    }
}
