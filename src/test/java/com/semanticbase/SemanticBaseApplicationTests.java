package com.semanticbase;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "spring.ai.openai.api-key=test-key",
        "spring.ai.openai.base-url=http://localhost:9999",
        "spring.ai.ollama.base-url=http://localhost:9998",
        "spring.ai.vectorstore.pgvector.initialize-schema=false"
})
class SemanticBaseApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
