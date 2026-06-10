package com.superagent.logistics.api.dto;

import java.util.List;

public record KnowledgePreviewResponse(
        String tenantId,
        String baseDocId,
        String docId,
        String title,
        String docType,
        String bizDomain,
        String version,
        int chunkCount,
        List<KnowledgeChunkResponse> chunks
) {
}
