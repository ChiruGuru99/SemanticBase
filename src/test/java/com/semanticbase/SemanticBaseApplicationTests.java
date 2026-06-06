package com.semanticbase;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

@Import(TestEmbeddingConfig.class)
class SemanticBaseApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
