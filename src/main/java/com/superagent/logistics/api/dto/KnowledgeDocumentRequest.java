package com.superagent.logistics.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

public record KnowledgeDocumentRequest(
        String tenantId,
        String userId,
        List<String> roles,
        String baseDocId,
        String docId,
        @NotBlank String title,
        @NotBlank String docType,
        @NotBlank String bizDomain,
        String version,
        String status,
        String sourceUrl,
        List<String> aclRoles,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @NotBlank String content,
        Boolean autoIndex
) {
}
