package com.superagent.logistics.knowledge;

public record KnowledgeRerankCandidate(
        KnowledgeChunk chunk,
        double vectorScore,
        double keywordScore,
        double ruleScore
) {
}
