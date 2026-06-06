package com.semanticbase.ingestion;

import com.semanticbase.AbstractIntegrationTest;
import com.semanticbase.TestEmbeddingConfig;
import com.semanticbase.ingestion.domain.Document;
import com.semanticbase.ingestion.domain.IngestionStatus;
import com.semanticbase.ingestion.pipeline.DocumentRepository;
import com.semanticbase.ingestion.pipeline.IngestionService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestEmbeddingConfig.class)
class IngestionPipelineIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    IngestionService ingestion;

    @Autowired
    DocumentRepository documents;

    @Autowired
    JdbcClient jdbc;

    @Test
    void ingestsTextFileEndToEnd() {
        String body = """
                Quarterly results memo. Contact alice@example.com or 555-123-4567 for questions.
                The company achieved record revenue this quarter, growing 25% year over year.
                Customer acquisition costs decreased while lifetime value increased.
                Looking ahead, we expect continued growth driven by enterprise adoption.
                """.repeat(8);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        IngestionService.IngestResult result = ingestion.submit(bytes, "text/plain", "memo.txt");
        assertThat(result.documentId()).isNotNull();
        assertThat(result.duplicate()).isFalse();

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Document d = documents.findById(result.documentId()).orElseThrow();
                    assertThat(d.status()).isIn(IngestionStatus.COMPLETED, IngestionStatus.FAILED);
                });

        Document persisted = documents.findById(result.documentId()).orElseThrow();
        assertThat(persisted.status()).isEqualTo(IngestionStatus.COMPLETED);

        Long chunkCount = jdbc.sql("""
                        SELECT COUNT(*) FROM chunks
                        WHERE metadata->>'document_id' = :docId
                        """)
                .param("docId", result.documentId().toString())
                .query(Long.class)
                .single();
        assertThat(chunkCount).isGreaterThan(0L);

        String firstChunk = jdbc.sql("""
                        SELECT content FROM chunks
                        WHERE metadata->>'document_id' = :docId
                        LIMIT 1
                        """)
                .param("docId", result.documentId().toString())
                .query(String.class)
                .single();
        assertThat(firstChunk).contains("[EMAIL]");
        assertThat(firstChunk).contains("[PHONE]");
        assertThat(firstChunk).doesNotContain("alice@example.com");
        assertThat(firstChunk).doesNotContain("555-123-4567");
    }

    @Test
    void duplicateUploadReturnsExistingDocument() {
        byte[] bytes = "duplicate test content".getBytes(StandardCharsets.UTF_8);
        IngestionService.IngestResult first = ingestion.submit(bytes, "text/plain", "dup.txt");
        IngestionService.IngestResult second = ingestion.submit(bytes, "text/plain", "dup.txt");

        assertThat(first.documentId()).isEqualTo(second.documentId());
        assertThat(second.duplicate()).isTrue();
    }

    @Test
    void unknownDocumentNotFound() {
        assertThat(documents.findById(UUID.randomUUID())).isEmpty();
    }
}
