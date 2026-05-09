package com.semanticbase.ingestion.extract;

public record ExtractedDocument(
        String text,
        String contentType,
        String sourceName
) {}
