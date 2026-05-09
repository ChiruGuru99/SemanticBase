package com.semanticbase.ingestion.pii;

public interface PiiRedactor {
    String redact(String input);
}
