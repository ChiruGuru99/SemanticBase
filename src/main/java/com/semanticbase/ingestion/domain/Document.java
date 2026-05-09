package com.semanticbase.ingestion.domain;

import java.time.Instant;
import java.util.UUID;

public record Document(
        UUID id,
        String tenantId,
        String sourceName,
        String contentType,
        long byteSize,
        String checksumSha256,
        IngestionStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
