package com.semanticbase.retrieval;

import com.semanticbase.retrieval.hybrid.RrfFusion;
import com.semanticbase.retrieval.lexical.LexicalSearcher;
import com.semanticbase.retrieval.rerank.CrossEncoderReranker;
import com.semanticbase.retrieval.vector.VectorSearcher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrievalService {

    private static final int CANDIDATE_K = 20;
    private static final int FINAL_K = 6;
    private static final String DEFAULT_TENANT = "default";

    private final VectorSearcher vector;
    private final LexicalSearcher lexical;
    private final RrfFusion fusion;
    private final CrossEncoderReranker reranker;

    public RetrievalService(VectorSearcher vector,
                            LexicalSearcher lexical,
                            RrfFusion fusion,
                            CrossEncoderReranker reranker) {
        this.vector = vector;
        this.lexical = lexical;
        this.fusion = fusion;
        this.reranker = reranker;
    }

    public List<RetrievedChunk> retrieve(String query, String tenantId) {
        return retrieve(query, tenantId == null ? DEFAULT_TENANT : tenantId, FINAL_K);
    }

    public List<RetrievedChunk> retrieve(String query, String tenantId, int topK) {
        if (query == null || query.isBlank()) return List.of();
        String tenant = tenantId == null ? DEFAULT_TENANT : tenantId;

        List<RetrievedChunk> vectorHits = vector.search(query, tenant, CANDIDATE_K);
        List<RetrievedChunk> lexicalHits = lexical.search(query, tenant, CANDIDATE_K);
        List<RetrievedChunk> fused = fusion.fuse(vectorHits, lexicalHits, CANDIDATE_K);
        return reranker.rerank(query, fused, topK);
    }
}
