package com.superagent.logistics.ops;

import com.superagent.logistics.knowledge.PgVectorKnowledgeStore;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("pgVector")
public class PgVectorHealthIndicator implements HealthIndicator {

    private final PgVectorKnowledgeStore vectorKnowledgeStore;

    public PgVectorHealthIndicator(PgVectorKnowledgeStore vectorKnowledgeStore) {
        this.vectorKnowledgeStore = vectorKnowledgeStore;
    }

    @Override
    public Health health() {
        Health.Builder builder = vectorKnowledgeStore.isEnabled() && !vectorKnowledgeStore.isReady()
                ? Health.status("DEGRADED")
                : Health.up();
        return builder
                .withDetail("enabled", vectorKnowledgeStore.isEnabled())
                .withDetail("ready", vectorKnowledgeStore.isReady())
                .withDetail("table", vectorKnowledgeStore.table())
                .withDetail("chunks", vectorKnowledgeStore.countChunks())
                .build();
    }
}
