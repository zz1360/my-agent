package com.superagent.logistics.knowledge;

public record KnowledgeSearchResult(
        KnowledgeChunk chunk,
        double score
) {
}
