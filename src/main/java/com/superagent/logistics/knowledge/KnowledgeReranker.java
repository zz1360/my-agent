package com.superagent.logistics.knowledge;

import java.util.List;

public interface KnowledgeReranker {

    List<KnowledgeSearchResult> rerank(String query, List<KnowledgeRerankCandidate> candidates, int topK);

    String providerName();
}
