package com.superagent.logistics.api.dto;

public record KnowledgeSearchHitResponse(
        String title,
        String docId,
        String chunkId,
        String excerpt,
        double score,
        double vectorScore,
        double keywordScore,
        double ruleScore,
        Double rerankerScore,
        String rerankerProvider
) {
}
