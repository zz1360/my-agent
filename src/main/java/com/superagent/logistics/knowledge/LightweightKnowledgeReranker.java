package com.superagent.logistics.knowledge;

import java.util.Comparator;
import java.util.List;

public class LightweightKnowledgeReranker implements KnowledgeReranker {

    @Override
    public List<KnowledgeSearchResult> rerank(String query, List<KnowledgeRerankCandidate> candidates, int topK) {
        return candidates.stream()
                .filter(candidate -> candidate.ruleScore() > 0)
                .sorted(Comparator.comparingDouble(KnowledgeRerankCandidate::ruleScore).reversed())
                .limit(Math.max(1, topK))
                .map(candidate -> new KnowledgeSearchResult(candidate.chunk(), candidate.ruleScore(),
                        candidate.vectorScore(), candidate.keywordScore(), candidate.ruleScore(), null, providerName()))
                .toList();
    }

    @Override
    public String providerName() {
        return "lightweight-rule";
    }
}
