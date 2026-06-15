package com.superagent.logistics.ops;

import com.superagent.logistics.config.RetrievalProperties;
import com.superagent.logistics.knowledge.PgVectorKnowledgeStore;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("retrieval")
public class RetrievalHealthIndicator implements HealthIndicator {

    private final RetrievalProperties retrievalProperties;
    private final PgVectorKnowledgeStore vectorKnowledgeStore;

    public RetrievalHealthIndicator(RetrievalProperties retrievalProperties, PgVectorKnowledgeStore vectorKnowledgeStore) {
        this.retrievalProperties = retrievalProperties;
        this.vectorKnowledgeStore = vectorKnowledgeStore;
    }

    @Override
    public Health health() {
        String defaultMode = retrievalProperties.normalizedDefaultMode();
        boolean vectorRequired = "VECTOR_ONLY".equals(defaultMode);
        Health.Builder builder = vectorRequired && !vectorKnowledgeStore.isReady()
                ? Health.status("OUT_OF_SERVICE")
                : Health.up();
        return builder
                .withDetail("defaultMode", defaultMode)
                .withDetail("vectorRequired", vectorRequired)
                .withDetail("vectorReady", vectorKnowledgeStore.isReady())
                .build();
    }
}
