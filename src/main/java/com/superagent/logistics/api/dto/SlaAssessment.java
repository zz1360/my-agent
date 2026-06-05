package com.superagent.logistics.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SlaAssessment(
        String waybillId,
        String serviceType,
        String status,
        String matchedSlaId,
        Integer promiseHours,
        LocalDateTime promisedDeliveryTime,
        LocalDateTime actualDeliveryTime,
        long delayHours,
        String compensationJudgement,
        String ruleSummary,
        List<String> evidence
) {
}
