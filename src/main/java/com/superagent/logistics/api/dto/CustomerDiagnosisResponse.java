package com.superagent.logistics.api.dto;

import com.superagent.logistics.business.CustomerProfile;
import com.superagent.logistics.business.DiagnosisReport;

import java.time.Instant;
import java.util.List;

public record CustomerDiagnosisResponse(
        String traceId,
        String conversationId,
        DiagnosisWindow window,
        CustomerProfile customer,
        DiagnosisReport diagnosis,
        List<RiskAttribution> attributions,
        List<SlaAssessment> slaAssessments,
        List<Citation> citations,
        List<ToolCallSummary> toolCalls,
        List<String> nextActions,
        String riskLevel,
        double confidence,
        String narrative,
        String modelProvider,
        Instant createdAt
) {
}
