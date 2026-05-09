package com.semanticbase.ingestion.api.dto;

import com.semanticbase.ingestion.domain.Document;
import com.semanticbase.ingestion.domain.IngestionStatus;

import java.time.Instant;
import java.util.UUID;

public record DocumentStatusResponse(
        UUID id,
        String sourceName,
        String contentType,
        long byteSize,
        IngestionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static DocumentStatusResponse from(Document d) {
        return new DocumentStatusResponse(
                d.id(),
                d.sourceName(),
                d.contentType(),
                d.byteSize(),
                d.status(),
                d.createdAt(),
                d.updatedAt()
        );
    }
}
