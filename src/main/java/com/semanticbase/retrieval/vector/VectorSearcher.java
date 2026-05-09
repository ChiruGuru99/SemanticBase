package com.semanticbase.retrieval.vector;

import com.semanticbase.retrieval.RetrievedChunk;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class VectorSearcher {

    private final VectorStore vectorStore;

    public VectorSearcher(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<RetrievedChunk> search(String query, String tenantId, int topK) {
        var filter = new FilterExpressionBuilder().eq("tenant_id", tenantId).build();
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(filter)
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        if (results == null) return List.of();
        return results.stream().map(this::toChunk).toList();
    }

    private RetrievedChunk toChunk(Document d) {
        var meta = d.getMetadata();
        Double score = d.getScore();
        return new RetrievedChunk(
                UUID.fromString(d.getId()),
                d.getText(),
                stringMeta(meta, "document_id"),
                stringMeta(meta, "source_name"),
                intMeta(meta, "chunk_index"),
                score == null ? 0d : score
        );
    }

    private static String stringMeta(java.util.Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    private static Integer intMeta(java.util.Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
