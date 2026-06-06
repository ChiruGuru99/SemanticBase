# SemanticBase

The source of truth for your private AI.

A document intelligence platform that lets teams chat with their internal corpus (PDFs, wikis, technical docs, spreadsheets) using hybrid retrieval over a private vector store.

## Stack

- Java 17, Spring Boot 3.4, Spring AI 1.0, Spring Cloud Config 2024.0
- Postgres 17 + pgvector (HNSW index, 768-dim embeddings)
- Apache Tika 3 for ingestion
- Groq (OpenAI-compatible API) for chat completion
- Ollama for embeddings (`nomic-embed-text`)
- Memurai/Redis for semantic + exact-match answer cache
- Flyway, Testcontainers, Micrometer + Prometheus

## v1 scope

- Hot-drop ingestion: Tika extraction, regex PII redaction, semantic chunking, embedding into pgvector
- Hybrid retrieval: vector + lexical (`tsvector`) with Reciprocal Rank Fusion
- RAG chat with citations, streaming via SSE
- Two-tier answer cache (exact-match SHA + semantic cosine, tenant-scoped)
- Minimal browser UI at `/`
- Observability: Prometheus metrics

Out of scope for v1: MCP tool integration, cross-encoder reranker (kept as a no-op interface), OCR.

## Prerequisites

- JDK 17 (JDK 21 recommended for production-safe virtual threads)
- Postgres 17 with the `pgvector` extension installed (default port 5432)
- Memurai or Redis 7 on `localhost:6379`
- Ollama running locally with `nomic-embed-text` pulled:
  ```powershell
  ollama pull nomic-embed-text
  ```
- Spring Cloud Config Server running on `http://localhost:8888` (or set `CONFIG_SERVER_URL`)
- A Groq API key from https://console.groq.com

## Configuration model

Application configuration is split between two locations:

- **`src/main/resources/application.yml`** — architectural config that's coupled to code or schema (vector dimension, model gates, Flyway, server port). Lives in this repo, rarely changes.
- **Spring Cloud Config Server** — environment-specific values and secrets (datasource, Redis, Groq key, Ollama URL, cache thresholds). Templates in [deploy/config-server/](deploy/config-server/) — copy these into the git repo backing your config server.

Startup is **fail-fast**: if the config server is unreachable on launch, the app refuses to start. This avoids running with a half-configured context.

### Bootstrap sequence

1. Config server boots on `:8888` and serves YAML from its backing git repo.
2. SemanticBase starts, pulls `semanticbase.yml` + `semanticbase-{profile}.yml` from the server.
3. Local `application.yml` is merged with the fetched config; server values win on conflict.
4. Placeholders like `${SPRING_DATASOURCE_PASSWORD}` and `${GROQ_API_KEY}` resolve from the SemanticBase JVM's env, **not** from the config repo (so secrets stay out of git).

## Quickstart

1. Create the database (one-time):
   ```sql
   CREATE DATABASE semanticbase;
   ```
2. Drop the templates from [deploy/config-server/](deploy/config-server/) into your config server's backing repo:
   - `semanticbase.yml`
   - `semanticbase-local.yml`
3. Verify your config server resolves the merged config:
   ```powershell
   curl.exe http://localhost:8888/semanticbase/local
   ```
4. Set the secrets as env vars on the SemanticBase host (PowerShell):
   ```powershell
   $env:SPRING_DATASOURCE_PASSWORD = "postgres"
   $env:GROQ_API_KEY = "gsk_..."
   ```
5. Run:
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
6. Open the UI: http://localhost:8080
7. Health check: http://localhost:8080/actuator/health

## Environment variables

| Env var | Default | Notes |
|---|---|---|
| `CONFIG_SERVER_URL` | `http://localhost:8888` | Spring Cloud Config Server URL |
| `SPRING_PROFILES_ACTIVE` | `local` | Selects which `semanticbase-{profile}.yml` to overlay |
| `SPRING_DATASOURCE_PASSWORD` | *(required)* | Resolved by config server template |
| `GROQ_API_KEY` | *(required)* | Resolved by config server template |
| `SERVER_PORT` | `8080` | |

## Development notes

- v1 sends queries and retrieved chunks to the Groq API at inference time. Full air-gapped deployment (self-hosted vLLM/llama.cpp) is a v2 concern.
- Embeddings stay local via Ollama, so document content is only sent to Groq at query time as part of the RAG context window.
- The pgvector dimension is fixed at 768 to match `nomic-embed-text`. Changing the embedding model requires a coordinated schema migration — keep this value in `application.yml`, not in the config server.
- The semantic cache threshold (`semanticbase.cache.semantic.threshold`) is tuned to 0.92 for `nomic-embed-text`; raise it toward 0.97 if you swap to a higher-quality embedding model.
