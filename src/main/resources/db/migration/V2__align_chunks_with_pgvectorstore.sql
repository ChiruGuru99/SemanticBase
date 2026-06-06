-- Spring AI's PgVectorStore expects a (id, content, metadata, embedding) layout.
-- v1 added domain columns directly on `chunks`; this migration moves them into
-- the `metadata` JSONB so PgVectorStore.add() works without schema customization,
-- while preserving hybrid retrieval via the generated tsv column.

ALTER TABLE chunks DROP CONSTRAINT IF EXISTS chunks_document_id_fkey;
DROP INDEX IF EXISTS idx_chunks_document;
DROP INDEX IF EXISTS idx_chunks_tenant;

UPDATE chunks
SET metadata = jsonb_strip_nulls(
        coalesce(metadata, '{}'::jsonb) ||
        jsonb_build_object(
            'document_id', document_id::text,
            'tenant_id',   tenant_id,
            'chunk_index', chunk_index
        )
    )
WHERE document_id IS NOT NULL OR tenant_id IS NOT NULL OR chunk_index IS NOT NULL;

ALTER TABLE chunks
    DROP COLUMN document_id,
    DROP COLUMN tenant_id,
    DROP COLUMN chunk_index,
    DROP COLUMN created_at;

ALTER TABLE chunks ALTER COLUMN content DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_chunks_metadata_tenant
    ON chunks ((metadata->>'tenant_id'));
CREATE INDEX IF NOT EXISTS idx_chunks_metadata_document
    ON chunks ((metadata->>'document_id'));
