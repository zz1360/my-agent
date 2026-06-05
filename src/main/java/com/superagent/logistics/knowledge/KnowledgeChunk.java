package com.superagent.logistics.knowledge;

public record KnowledgeChunk(
        String docId,
        String chunkId,
        String title,
        String content,
        String metadata,
        String aclRoles
) {
}
