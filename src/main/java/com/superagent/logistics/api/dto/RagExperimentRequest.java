package com.superagent.logistics.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record RagExperimentRequest(
        String tenantId,
        String userId,
        List<String> roles,
        String experimentId,
        @NotBlank String name,
        String description,
        @NotBlank String query,
        List<String> expectedDocIds,
        List<String> expectedChunkIds,
        Integer topK,
        List<String> modes,
        Boolean enabled
) {
}
