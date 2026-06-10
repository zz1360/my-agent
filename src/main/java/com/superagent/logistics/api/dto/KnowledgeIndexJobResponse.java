package com.superagent.logistics.api.dto;

import java.time.Instant;

public record KnowledgeIndexJobResponse(
        String tenantId,
        String jobId,
        String triggerType,
        String requestedBy,
        String status,
        String documentId,
        String baseDocId,
        int chunkCount,
        boolean vectorEnabled,
        boolean vectorReady,
        String tableName,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
}
