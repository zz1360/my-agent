package com.superagent.logistics.api.dto;

import java.time.Instant;
import java.util.List;

public record RagExperimentResponse(
        String tenantId,
        String experimentId,
        String name,
        String description,
        String userId,
        List<String> roles,
        String query,
        List<String> expectedDocIds,
        List<String> expectedChunkIds,
        int topK,
        List<String> modes,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
