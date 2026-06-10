package com.superagent.logistics.api.dto;

public record KnowledgeReindexResponse(
        String tenantId,
        String jobId,
        String status,
        int chunks,
        boolean vectorEnabled,
        boolean vectorReady,
        String table
) {
}
