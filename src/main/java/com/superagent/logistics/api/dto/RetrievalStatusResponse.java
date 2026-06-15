package com.superagent.logistics.api.dto;

public record RetrievalStatusResponse(
        String defaultMode,
        boolean vectorStoreEnabled,
        boolean vectorStoreReady,
        String vectorTable,
        int vectorChunks
) {
}
