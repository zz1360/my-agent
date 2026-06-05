package com.superagent.logistics.api.dto;

public record KnowledgeReindexResponse(
        String tenantId,
        int chunks,
        boolean vectorEnabled,
        boolean vectorReady,
        String table
) {
}
