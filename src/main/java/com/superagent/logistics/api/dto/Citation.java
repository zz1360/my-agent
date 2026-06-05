package com.superagent.logistics.api.dto;

public record Citation(
        String type,
        String title,
        String docId,
        String chunkId,
        String excerpt
) {
}
