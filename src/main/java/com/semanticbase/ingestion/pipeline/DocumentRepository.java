package com.semanticbase.ingestion.pipeline;

import com.semanticbase.ingestion.domain.Document;
import com.semanticbase.ingestion.domain.IngestionStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DocumentRepository {

    private final JdbcClient jdbc;

    public DocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Document> findByTenantAndChecksum(String tenantId, String checksum) {
        try {
            Document d = jdbc.sql("""
                            SELECT id, tenant_id, source_name, content_type, byte_size,
                                   checksum_sha256, status, created_at, updated_at
                            FROM documents
                            WHERE tenant_id = :tenant AND checksum_sha256 = :checksum
                            """)
                    .param("tenant", tenantId)
                    .param("checksum", checksum)
                    .query(this::mapDocument)
                    .single();
            return Optional.of(d);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Document insertPending(String tenantId, String sourceName, String contentType,
                                  long byteSize, String checksum) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.sql("""
                        INSERT INTO documents (id, tenant_id, source_name, content_type,
                                               byte_size, checksum_sha256, status, created_at, updated_at)
                        VALUES (:id, :tenant, :name, :ct, :size, :checksum, :status, :now, :now)
                        """)
                .param("id", id)
                .param("tenant", tenantId)
                .param("name", sourceName)
                .param("ct", contentType)
                .param("size", byteSize)
                .param("checksum", checksum)
                .param("status", IngestionStatus.PENDING.name())
                .param("now", Timestamp.from(now))
                .update();
        return new Document(id, tenantId, sourceName, contentType, byteSize, checksum,
                IngestionStatus.PENDING, now, now);
    }

    public void updateStatus(UUID documentId, IngestionStatus status) {
        jdbc.sql("UPDATE documents SET status = :status, updated_at = :now WHERE id = :id")
                .param("id", documentId)
                .param("status", status.name())
                .param("now", Timestamp.from(Instant.now()))
                .update();
    }

    public java.util.List<Document> findRecent(String tenantId, int limit) {
        return jdbc.sql("""
                        SELECT id, tenant_id, source_name, content_type, byte_size,
                               checksum_sha256, status, created_at, updated_at
                        FROM documents
                        WHERE tenant_id = :tenant
                        ORDER BY created_at DESC
                        LIMIT :limit
                        """)
                .param("tenant", tenantId)
                .param("limit", limit)
                .query(this::mapDocument)
                .list();
    }

    public Optional<Document> findById(UUID id) {
        try {
            Document d = jdbc.sql("""
                            SELECT id, tenant_id, source_name, content_type, byte_size,
                                   checksum_sha256, status, created_at, updated_at
                            FROM documents WHERE id = :id
                            """)
                    .param("id", id)
                    .query(this::mapDocument)
                    .single();
            return Optional.of(d);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private Document mapDocument(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Document(
                (UUID) rs.getObject("id"),
                rs.getString("tenant_id"),
                rs.getString("source_name"),
                rs.getString("content_type"),
                rs.getLong("byte_size"),
                rs.getString("checksum_sha256"),
                IngestionStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
