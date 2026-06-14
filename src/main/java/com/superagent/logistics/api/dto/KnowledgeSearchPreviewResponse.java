package com.superagent.logistics.api.dto;

import java.util.List;

public record KnowledgeSearchPreviewResponse(
        String mode,
        boolean vectorReady,
        int topK,
        List<KnowledgeSearchHitResponse> hits
) {
}
