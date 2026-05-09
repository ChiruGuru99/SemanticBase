package com.semanticbase.retrieval.hybrid;

import com.semanticbase.retrieval.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class RrfFusion {

    private static final int DEFAULT_K = 60;

    public List<RetrievedChunk> fuse(List<RetrievedChunk> vector, List<RetrievedChunk> lexical, int topK) {
        return fuse(vector, lexical, topK, DEFAULT_K);
    }

    public List<RetrievedChunk> fuse(List<RetrievedChunk> vector, List<RetrievedChunk> lexical, int topK, int k) {
        Map<UUID, Double> scores = new LinkedHashMap<>();
        Map<UUID, RetrievedChunk> byId = new LinkedHashMap<>();

        accumulate(vector, scores, byId, k);
        accumulate(lexical, scores, byId, k);

        return scores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(e -> byId.get(e.getKey()).withScore(e.getValue()))
                .toList();
    }

    private static void accumulate(List<RetrievedChunk> list,
                                    Map<UUID, Double> scores,
                                    Map<UUID, RetrievedChunk> byId,
                                    int k) {
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            RetrievedChunk c = list.get(i);
            scores.merge(c.id(), 1.0 / (k + i + 1), Double::sum);
            byId.putIfAbsent(c.id(), c);
        }
    }
}
