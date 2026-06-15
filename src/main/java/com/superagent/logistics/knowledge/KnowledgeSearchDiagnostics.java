package com.superagent.logistics.knowledge;

import java.util.List;

public record KnowledgeSearchDiagnostics(
        String query,
        String retrievalMode,
        int requestedTopK,
        boolean vectorReady,
        boolean vectorUsed,
        boolean keywordUsed,
        boolean rerankerUsed,
        int activeChunkCount,
        int candidateCount,
        int rerankCandidateCount,
        int returnedCount,
        String knowledgeVersion,
        List<KnowledgeSearchResult> results
) {
}
