package com.semanticbase.retrieval;

import java.util.UUID;

public record RetrievedChunk(
        UUID id,
        String content,
        String documentId,
        String sourceName,
        Integer chunkIndex,
        double score
) {
    public RetrievedChunk withScore(double newScore) {
        return new RetrievedChunk(id, content, documentId, sourceName, chunkIndex, newScore);
    }
}
