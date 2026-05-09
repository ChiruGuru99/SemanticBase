package com.semanticbase.retrieval.lexical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semanticbase.retrieval.RetrievedChunk;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Component
public class LexicalSearcher {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public LexicalSearcher(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<RetrievedChunk> search(String query, String tenantId, int topK) {
        if (query == null || query.isBlank()) return List.of();
        return jdbc.sql("""
                        SELECT id,
                               content,
                               metadata,
                               ts_rank_cd(tsv, websearch_to_tsquery('english', :q)) AS score
                        FROM chunks
                        WHERE metadata->>'tenant_id' = :tenant
                          AND tsv @@ websearch_to_tsquery('english', :q)
                        ORDER BY score DESC
                        LIMIT :limit
                        """)
                .param("q", query)
                .param("tenant", tenantId)
                .param("limit", topK)
                .query(this::mapRow)
                .list();
    }

    private RetrievedChunk mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        String content = rs.getString("content");
        String metadataJson = rs.getString("metadata");
        double score = rs.getDouble("score");

        JsonNode meta = parseMetadata(metadataJson);
        String documentId = textOrNull(meta, "document_id");
        String sourceName = textOrNull(meta, "source_name");
        Integer chunkIndex = intOrNull(meta, "chunk_index");
        return new RetrievedChunk(id, content, documentId, sourceName, chunkIndex, score);
    }

    private JsonNode parseMetadata(String json) {
        if (json == null || json.isBlank()) return objectMapper.nullNode();
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return objectMapper.nullNode();
        }
    }

    private static String textOrNull(JsonNode node, String key) {
        JsonNode v = node.get(key);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Integer intOrNull(JsonNode node, String key) {
        JsonNode v = node.get(key);
        return v == null || v.isNull() ? null : v.asInt();
    }
}
