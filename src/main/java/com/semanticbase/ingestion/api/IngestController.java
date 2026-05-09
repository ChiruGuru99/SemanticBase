package com.semanticbase.ingestion.api;

import com.semanticbase.ingestion.api.dto.DocumentStatusResponse;
import com.semanticbase.ingestion.api.dto.IngestResponse;
import com.semanticbase.ingestion.pipeline.DocumentRepository;
import com.semanticbase.ingestion.pipeline.IngestionService;
import com.semanticbase.ingestion.pipeline.IngestionService.IngestResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class IngestController {

    private final IngestionService ingestion;
    private final DocumentRepository documents;

    public IngestController(IngestionService ingestion, DocumentRepository documents) {
        this.ingestion = ingestion;
        this.documents = documents;
    }

    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestResponse> ingest(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is empty");
        }
        String contentType = file.getContentType() != null
                ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String sourceName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "unnamed";

        IngestResult result = ingestion.submit(file.getBytes(), contentType, sourceName);

        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status)
                .body(new IngestResponse(result.documentId(), result.status(), result.duplicate()));
    }

    @GetMapping("/documents/{id}")
    public DocumentStatusResponse getDocument(@PathVariable UUID id) {
        return documents.findById(id)
                .map(DocumentStatusResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found"));
    }

    @GetMapping("/documents")
    public List<DocumentStatusResponse> listDocuments(
            @RequestParam(defaultValue = "default") String tenantId,
            @RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return documents.findRecent(tenantId, safeLimit).stream()
                .map(DocumentStatusResponse::from)
                .toList();
    }
}
