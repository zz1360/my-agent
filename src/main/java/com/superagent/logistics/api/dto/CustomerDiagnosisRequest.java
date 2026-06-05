package com.superagent.logistics.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

public record CustomerDiagnosisRequest(
        String conversationId,
        String userId,
        String tenantId,
        List<String> roles,
        @NotBlank String customerId,
        LocalDate from,
        LocalDate to,
        Integer days,
        String message,
        Boolean returnCitations
) {
}
