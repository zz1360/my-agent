package com.superagent.logistics.knowledge;

public record KnowledgeSearchOptions(
        boolean useVector,
        boolean useKeyword,
        boolean useReranker
) {
    public static KnowledgeSearchOptions defaults() {
        return new KnowledgeSearchOptions(true, true, true);
    }

    public static KnowledgeSearchOptions keywordOnly() {
        return new KnowledgeSearchOptions(false, true, false);
    }

    public static KnowledgeSearchOptions vectorOnly() {
        return new KnowledgeSearchOptions(true, false, false);
    }

    public static KnowledgeSearchOptions hybridWithoutReranker() {
        return new KnowledgeSearchOptions(true, true, false);
    }

    public static KnowledgeSearchOptions hybridWithReranker() {
        return defaults();
    }
}
