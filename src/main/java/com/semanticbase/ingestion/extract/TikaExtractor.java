package com.semanticbase.ingestion.extract;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class TikaExtractor {

    public ExtractedDocument extract(byte[] bytes, String contentType, String sourceName) {
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return sourceName;
            }
        };
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> docs = reader.get();
        String text = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
        return new ExtractedDocument(text, contentType, sourceName);
    }
}
