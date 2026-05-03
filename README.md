# SemanticBase

The source of truth for your private AI.

A document intelligence platform that lets teams chat with their internal corpus (PDFs, wikis, technical docs, spreadsheets) using hybrid retrieval over a private vector store.

## Stack

- Java 21, Spring Boot 3.4, Spring AI 1.0
- Postgres 17 + pgvector (HNSW index, 768-dim embeddings)
- Apache Tika 3 for ingestion
- Groq (OpenAI-compatible API) for chat completion
- Ollama for embeddings (`nomic-embed-text`)
- Flyway, Testcontainers, Micrometer + Prometheus

## v1 scope

- Hot-drop ingestion: Tika extraction, regex PII redaction, semantic chunking, embedding into pgvector
- Hybrid retrieval: vector + lexical (`tsvector`) with Reciprocal Rank Fusion
- RAG chat with citations, streaming via SSE
- Redis-backed semantic cache (Phase 4)
- Observability: Prometheus metrics, OTLP traces

Out of scope for v1: MCP tool integration, cross-encoder reranker (kept as a no-op interface).

## Prerequisites

- JDK 21
- Maven 3.9+
- Postgres 17 with the `pgvector` extension installed (default port 5432).
- Ollama running locally with `nomic-embed-text` pulled:
  ```powershell
  ollama pull nomic-embed-text
  ```
- A Groq API key from https://console.groq.com

## Quickstart

1. Create the database (one-time):
   ```sql
   CREATE DATABASE semanticbase;
   ```
2. Set environment variables (PowerShell):
   ```powershell
   $env:SPRING_DATASOURCE_PASSWORD = "postgres"
   $env:GROQ_API_KEY = "gsk_..."
   ```
3. Run:
   ```powershell
   mvn spring-boot:run
   ```
4. Health check: http://localhost:8080/actuator/health

## Configuration

| Env var | Default | Notes |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/semanticbase` | Override for non-default port |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | |
| `SPRING_DATASOURCE_PASSWORD` | *(required)* | |
| `GROQ_API_KEY` | *(required)* | https://console.groq.com |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | |

## Development notes

- v1 sends queries and retrieved chunks to the Groq API at inference time. Full air-gapped deployment (self-hosted vLLM/llama.cpp) is a v2 concern.
- Embeddings stay local via Ollama, so document content is only sent to Groq at query time as part of the RAG context window.
- The pgvector dimension is fixed at 768 to match `nomic-embed-text`. Changing the embedding model requires a schema migration.
