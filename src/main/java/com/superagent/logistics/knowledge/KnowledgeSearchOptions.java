package com.superagent.logistics.knowledge;

import java.util.Locale;

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

    public static KnowledgeSearchOptions fromMode(String mode) {
        return switch (normalizeMode(mode)) {
            case "KEYWORD_ONLY" -> keywordOnly();
            case "VECTOR_ONLY" -> vectorOnly();
            case "HYBRID_RULE" -> hybridWithoutReranker();
            case "HYBRID_RERANKER" -> hybridWithReranker();
            default -> throw new IllegalArgumentException("不支持的检索模式：" + mode);
        };
    }

    public static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "HYBRID_RERANKER";
        }
        String normalized = mode.trim()
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "KEYWORD", "KEYWORD_ONLY" -> "KEYWORD_ONLY";
            case "VECTOR", "VECTOR_ONLY" -> "VECTOR_ONLY";
            case "HYBRID", "HYBRID_RULE", "RULE" -> "HYBRID_RULE";
            case "HYBRID_RERANKER", "RERANKER", "HYBRID_WITH_RERANKER" -> "HYBRID_RERANKER";
            default -> throw new IllegalArgumentException("不支持的检索模式：" + mode);
        };
    }
}
