package com.superagent.logistics.api.dto;

public record KnowledgeChunkResponse(
        String docId,
        String chunkId,
        String title,
        String excerpt,
        String metadata,
        String aclRoles
) {
}
