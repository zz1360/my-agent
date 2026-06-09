package com.superagent.logistics.knowledge;

public record KnowledgeSearchResult(
        KnowledgeChunk chunk,
        double score,
        double vectorScore,
        double keywordScore,
        double ruleScore,
        Double rerankerScore,
        String rerankerProvider
) {
    public KnowledgeSearchResult(KnowledgeChunk chunk, double score) {
        this(chunk, score, 0, 0, score, null, "none");
    }
}
