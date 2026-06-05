package com.superagent.logistics.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record KnowledgeDocumentResponse(
        String tenantId,
        String docId,
        String title,
        String docType,
        String bizDomain,
        String version,
        String sourceUrl,
        String aclRoles,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        String content,
        int chunkCount,
        List<KnowledgeChunkResponse> chunks,
        Instant createdAt,
        Instant updatedAt
) {
}
