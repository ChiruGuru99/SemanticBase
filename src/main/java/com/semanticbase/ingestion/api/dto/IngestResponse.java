package com.semanticbase.ingestion.api.dto;

import com.semanticbase.ingestion.domain.IngestionStatus;

import java.util.UUID;

public record IngestResponse(
        UUID documentId,
        IngestionStatus status,
        boolean duplicate
) {}
