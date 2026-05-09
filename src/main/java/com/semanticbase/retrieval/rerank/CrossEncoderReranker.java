package com.semanticbase.retrieval.rerank;

import com.semanticbase.retrieval.RetrievedChunk;

import java.util.List;

public interface CrossEncoderReranker {
    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topK);
}
