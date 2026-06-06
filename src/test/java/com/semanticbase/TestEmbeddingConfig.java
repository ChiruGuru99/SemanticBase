package com.semanticbase;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Random;

/**
 * Deterministic stub embedding model for integration tests. Avoids hitting Ollama.
 * Same input string always yields the same vector (seeded RNG).
 */
@TestConfiguration
public class TestEmbeddingConfig {

    public static final int DIM = 768;

    @Bean
    @Primary
    public EmbeddingModel testEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                List<Embedding> out = new java.util.ArrayList<>();
                for (int i = 0; i < request.getInstructions().size(); i++) {
                    out.add(new Embedding(deterministicVector(request.getInstructions().get(i)), i));
                }
                return new EmbeddingResponse(out);
            }

            @Override
            public float[] embed(Document document) {
                return deterministicVector(document.getText());
            }
        };
    }

    private static float[] deterministicVector(String text) {
        Random r = new Random(text == null ? 0 : text.hashCode());
        float[] v = new float[DIM];
        float norm = 0f;
        for (int i = 0; i < DIM; i++) {
            v[i] = (float) (r.nextGaussian());
            norm += v[i] * v[i];
        }
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < DIM; i++) v[i] /= norm;
        return v;
    }
}
