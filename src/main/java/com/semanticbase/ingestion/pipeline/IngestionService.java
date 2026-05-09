package com.semanticbase.ingestion.pipeline;

import com.semanticbase.ingestion.chunk.SpringAiChunker;
import com.semanticbase.ingestion.domain.Document;
import com.semanticbase.ingestion.domain.IngestionStatus;
import com.semanticbase.ingestion.extract.ExtractedDocument;
import com.semanticbase.ingestion.extract.TikaExtractor;
import com.semanticbase.ingestion.pii.PiiRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final String DEFAULT_TENANT = "default";

    private final TikaExtractor extractor;
    private final PiiRedactor redactor;
    private final SpringAiChunker chunker;
    private final VectorStore vectorStore;
    private final DocumentRepository documents;

    public IngestionService(TikaExtractor extractor,
                            PiiRedactor redactor,
                            SpringAiChunker chunker,
                            VectorStore vectorStore,
                            DocumentRepository documents) {
        this.extractor = extractor;
        this.redactor = redactor;
        this.chunker = chunker;
        this.vectorStore = vectorStore;
        this.documents = documents;
    }

    public IngestResult submit(byte[] bytes, String contentType, String sourceName) {
        return submit(bytes, contentType, sourceName, DEFAULT_TENANT);
    }

    public IngestResult submit(byte[] bytes, String contentType, String sourceName, String tenantId) {
        String checksum = sha256(bytes);
        return documents.findByTenantAndChecksum(tenantId, checksum)
                .map(existing -> new IngestResult(existing.id(), existing.status(), true))
                .orElseGet(() -> {
                    Document created = documents.insertPending(tenantId, sourceName, contentType,
                            bytes.length, checksum);
                    processAsync(created.id(), bytes, contentType, sourceName, tenantId);
                    return new IngestResult(created.id(), IngestionStatus.PENDING, false);
                });
    }

    @Async("ingestionExecutor")
    public void processAsync(UUID documentId, byte[] bytes, String contentType,
                             String sourceName, String tenantId) {
        try {
            documents.updateStatus(documentId, IngestionStatus.PROCESSING);
            ExtractedDocument extracted = extractor.extract(bytes, contentType, sourceName);
            String redacted = redactor.redact(extracted.text());
            List<String> chunks = chunker.chunk(redacted);

            if (chunks.isEmpty()) {
                log.warn("Document {} produced no chunks (empty or unreadable content)", documentId);
                documents.updateStatus(documentId, IngestionStatus.COMPLETED);
                return;
            }

            List<org.springframework.ai.document.Document> aiDocs = new java.util.ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = Map.of(
                        "document_id", documentId.toString(),
                        "tenant_id", tenantId,
                        "chunk_index", i,
                        "source_name", sourceName
                );
                aiDocs.add(new org.springframework.ai.document.Document(chunks.get(i), metadata));
            }
            vectorStore.add(aiDocs);

            documents.updateStatus(documentId, IngestionStatus.COMPLETED);
            log.info("Ingested document {} ({} chunks)", documentId, chunks.size());
        } catch (Exception e) {
            log.error("Ingestion failed for document {}", documentId, e);
            documents.updateStatus(documentId, IngestionStatus.FAILED);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record IngestResult(UUID documentId, IngestionStatus status, boolean duplicate) {}
}
