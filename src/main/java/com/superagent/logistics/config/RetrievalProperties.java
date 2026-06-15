package com.superagent.logistics.config;

import com.superagent.logistics.knowledge.KnowledgeSearchOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.retrieval")
public class RetrievalProperties {

    private String defaultMode = "hybrid_reranker";

    public String getDefaultMode() {
        return defaultMode;
    }

    public void setDefaultMode(String defaultMode) {
        this.defaultMode = defaultMode;
    }

    public String normalizedDefaultMode() {
        return KnowledgeSearchOptions.normalizeMode(defaultMode);
    }

    public KnowledgeSearchOptions defaultOptions() {
        return KnowledgeSearchOptions.fromMode(defaultMode);
    }
}
