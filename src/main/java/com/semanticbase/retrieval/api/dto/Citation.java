package com.semanticbase.retrieval.api.dto;

import com.semanticbase.retrieval.RetrievedChunk;

import java.util.UUID;

public record Citation(
        int n,
        UUID chunkId,
        String documentId,
        String sourceName,
        Integer chunkIndex,
        double score,
        String snippet
) {
    private static final int SNIPPET_LEN = 240;

    public static Citation of(int n, RetrievedChunk c) {
        String text = c.content() == null ? "" : c.content().strip();
        String snippet = text.length() > SNIPPET_LEN ? text.substring(0, SNIPPET_LEN) + "…" : text;
        return new Citation(n, c.id(), c.documentId(), c.sourceName(), c.chunkIndex(), c.score(), snippet);
    }
}
