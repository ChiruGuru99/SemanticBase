package com.semanticbase.retrieval.rerank;

import com.semanticbase.retrieval.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NoOpReranker implements CrossEncoderReranker {

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        return candidates.stream().limit(topK).toList();
    }
}
