CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'default',
    source_name     VARCHAR(512) NOT NULL,
    content_type    VARCHAR(128) NOT NULL,
    byte_size       BIGINT       NOT NULL,
    checksum_sha256 CHAR(64)     NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    metadata        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_tenant       ON documents(tenant_id);
CREATE INDEX idx_documents_status       ON documents(status);
CREATE UNIQUE INDEX uq_documents_tenant_checksum ON documents(tenant_id, checksum_sha256);

CREATE TABLE chunks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID         NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    tenant_id   VARCHAR(64)  NOT NULL DEFAULT 'default',
    chunk_index INTEGER      NOT NULL,
    content     TEXT         NOT NULL,
    embedding   vector(768),
    metadata    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    tsv         tsvector GENERATED ALWAYS AS (to_tsvector('english', content)) STORED,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_chunks_document ON chunks(document_id);
CREATE INDEX idx_chunks_tenant   ON chunks(tenant_id);
CREATE INDEX idx_chunks_tsv      ON chunks USING GIN(tsv);
CREATE INDEX idx_chunks_embedding_hnsw
    ON chunks USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE TABLE chat_sessions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   VARCHAR(64)  NOT NULL DEFAULT 'default',
    user_ref    VARCHAR(128),
    title       VARCHAR(256),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_sessions_tenant ON chat_sessions(tenant_id);

CREATE TABLE chat_messages (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID         NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role        VARCHAR(16)  NOT NULL,
    content     TEXT         NOT NULL,
    citations   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    token_count INTEGER,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_session ON chat_messages(session_id);
