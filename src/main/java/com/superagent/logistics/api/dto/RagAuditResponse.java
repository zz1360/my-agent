package com.superagent.logistics.api.dto;

import java.time.Instant;
import java.util.List;

public record RagAuditResponse(
        String traceId,
        String retrievalMode,
        String knowledgeVersion,
        int topK,
        boolean vectorReady,
        boolean vectorUsed,
        boolean keywordUsed,
        boolean rerankerUsed,
        int activeChunkCount,
        int candidateCount,
        int rerankCandidateCount,
        int returnedCount,
        List<KnowledgeSearchHitResponse> hits,
        Instant createdAt
) {
}
