package com.semanticbase.ingestion.domain;

import java.util.UUID;

public record Chunk(
        UUID documentId,
        String tenantId,
        int chunkIndex,
        String content
) {}
