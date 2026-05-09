package com.semanticbase.ingestion.chunk;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpringAiChunker {

    private static final int CHUNK_TOKENS = 512;
    private static final int MIN_CHUNK_SIZE_CHARS = 350;
    private static final int MIN_CHUNK_LENGTH_TO_EMBED = 5;
    private static final int MAX_NUM_CHUNKS = 10_000;
    private static final boolean KEEP_SEPARATOR = true;

    private final TokenTextSplitter splitter = new TokenTextSplitter(
            CHUNK_TOKENS,
            MIN_CHUNK_SIZE_CHARS,
            MIN_CHUNK_LENGTH_TO_EMBED,
            MAX_NUM_CHUNKS,
            KEEP_SEPARATOR
    );

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) return List.of();
        Document source = new Document(text);
        return splitter.apply(List.of(source)).stream()
                .map(Document::getText)
                .toList();
    }
}
