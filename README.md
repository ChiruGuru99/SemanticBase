# SemanticBase

Chat with your team's documents: hybrid-retrieval RAG over a vector store you run, with a pluggable LLM provider.

A document intelligence platform that lets teams chat with their internal corpus (PDFs, wikis, technical docs, spreadsheets) using hybrid retrieval over a vector store you run.

Documents are ingested and stored on your own infrastructure. Embeddings and answers come from configurable model providers, which are hosted third-party services unless you point them at model servers you run (see [Data flow](#data-flow)).

## Stack

- Java 17, Spring Boot 3.4, Spring AI 1.0, Spring Cloud Config 2024.0
- Postgres 17 + pgvector (HNSW index, 768-dim embeddings)
- Apache Tika 3 for ingestion
- Any OpenAI-compatible chat provider: Groq, Cloudflare Workers AI, NVIDIA NIM, Azure AI Foundry, or self-hosted vLLM
- Any OpenAI-compatible embedding provider with 768-dim output: Ollama (`nomic-embed-text`), Cloudflare Workers AI, OpenAI, Azure
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
- An embedding provider (see [Embedding providers](#embedding-providers)). The config template uses Ollama locally:
  ```powershell
  ollama pull nomic-embed-text
  ```
- Spring Cloud Config Server running on `http://localhost:8888` (or set `CONFIG_SERVER_URL`)
- An API key for a chat provider (see [Chat providers](#chat-providers)); the config template uses Groq's free tier

## Configuration model

Application configuration is split between two locations:

- **`src/main/resources/application.yml`** — architectural config that's coupled to code or schema (vector dimension, model gates, Flyway, server port). Lives in this repo, rarely changes.
- **Spring Cloud Config Server** — environment-specific values and secrets (datasource, Redis, chat and embedding provider URL/key/model, cache thresholds). Templates in [deploy/config-server/](deploy/config-server/) — copy these into the git repo backing your config server.

Startup is **fail-fast**: if the config server is unreachable on launch, the app refuses to start. This avoids running with a half-configured context.

### Bootstrap sequence

1. Config server boots on `:8888` and serves YAML from its backing git repo.
2. SemanticBase starts, pulls `semanticbase.yml` + `semanticbase-{profile}.yml` from the server.
3. Local `application.yml` is merged with the fetched config; server values win on conflict.
4. Placeholders like `${SPRING_DATASOURCE_PASSWORD}` and `${LLM_API_KEY}` resolve from the SemanticBase JVM's env, **not** from the config repo (so secrets stay out of git).

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
   $env:LLM_API_KEY = "..."
   $env:EMBEDDING_API_KEY = "..."   # any non-empty value for Ollama
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
| `LLM_API_KEY` | *(required)* | Chat provider API key; resolved by config server template |
| `EMBEDDING_API_KEY` | *(required)* | Embedding provider API key (any non-empty value for Ollama); resolved by config server template |
| `SERVER_PORT` | `8080` | |

## Chat providers

Chat goes through Spring AI's OpenAI client, so any OpenAI-compatible endpoint works. Set `spring.ai.openai.base-url` and
`spring.ai.openai.chat.options.model` in the config repo ([semanticbase.yml](deploy/config-server/semanticbase.yml), or a
profile override), and the provider's key in the `LLM_API_KEY` env var. No code change is needed to switch.
Free tiers change often; this table was checked on 2026-11-03.

| Provider | `model` | `base-url` | Cost | Notes |
|---|---|---|---|---|
| Groq (in the template) | `openai/gpt-oss-120b` | `https://api.groq.com/openai` | Free tier, no card | Llama 3.3 70B left Groq's free tier on 2026-08-16. Tight per-minute token limit; pace evals with `eval.delay-ms`. |
| Cloudflare Workers AI | `@cf/meta/llama-3.3-70b-instruct-fp8-fast` | `https://api.cloudflare.com/client/v4/accounts/<ACCOUNT_ID>/ai` | 10,000 Neurons/day free, no card | Roughly 75–150 RAG questions/day on the 70B. Key is a Workers AI API token. |
| NVIDIA NIM | `meta/llama-3.3-70b-instruct` | `https://integrate.api.nvidia.com` | Free developer credits | Key from build.nvidia.com. |
| Azure AI Foundry | your deployment name | `https://<resource>.openai.azure.com/openai` | Paid, covered by Azure free/student credits | Uses the v1 API; deploy e.g. Llama-3.3-70B-Instruct or gpt-oss-120b. |
| Self-hosted vLLM | served model name (`GET /v1/models`) | your server URL | Your GPU | The only option where queries and chunks never reach a third party. |

## Embedding providers

Embeddings use the same OpenAI-compatible client, configured separately so chat and embeddings can come from different
providers. Set `spring.ai.openai.embedding.base-url` and `spring.ai.openai.embedding.options.model` in the config repo,
and the key in `EMBEDDING_API_KEY`. Groq serves no embedding models.

The schema stores 768-dim vectors, so the model must output 768 dimensions. Vectors from different models are not
comparable: after switching, re-ingest your documents and purge the answer cache (`DELETE /api/cache/{tenantId}`).

| Provider | `model` | `base-url` | Cost | Notes |
|---|---|---|---|---|
| Ollama (in the template) | `nomic-embed-text` | `http://localhost:11434` | Free, runs on CPU | Ingested text stays on your machine. |
| Cloudflare Workers AI | `@cf/baai/bge-base-en-v1.5` | `https://api.cloudflare.com/client/v4/accounts/<ACCOUNT_ID>/ai` | Shares the 10,000 Neurons/day free allowance | 512-token input limit; chunks are up to 512 tokens, so long ones may be truncated. |
| OpenAI | `text-embedding-3-small` | `https://api.openai.com` | Paid | Also set `options.dimensions: 768`. |
| Azure AI Foundry | your `text-embedding-3-small` deployment | `https://<resource>.openai.azure.com/openai` | Covered by Azure credits | Also set `options.dimensions: 768`. |

## Evals

End-to-end RAG eval against a running instance (the configured embedding and chat providers). It ingests a small fictional
corpus ([src/test/resources/eval/corpus/](src/test/resources/eval/corpus/)) into an isolated `eval` tenant, asks the
questions in [questions.json](src/test/resources/eval/questions.json), and fails if any metric drops below its minimum:

- **hit@1 / MRR**: rank of the expected source document among the returned citations
- **fact recall**: expected facts present in the answer
- **abstention**: unanswerable questions get "I don't know"

Start the app, then:

```powershell
.\mvnw.cmd test -Peval
```

Settings live in [eval.properties](src/test/resources/eval/eval.properties) (`eval.base-url`, `eval.delay-ms`); override one-off with
`-Deval.base-url=...` or `-Deval.delay-ms=10000` to stay under a free tier's per-minute token limit.
The set is ~26 questions, so one question moves a metric by ~4 points; don't read smaller deltas as signal.
The default `mvnw test` excludes evals; it runs the unit tests plus Testcontainers integration tests, which are skipped when Docker isn't available.

## CI

- **[ci.yml](.github/workflows/ci.yml)** runs on every push and pull request: build, unit tests, and the Testcontainers
  integration tests (GitHub runners have Docker, so nothing is skipped there).
- **[eval.yml](.github/workflows/eval.yml)** is manual: Actions → *RAG eval* → *Run workflow*. It starts Postgres
  (pgvector), Redis and Ollama as service containers, loads [semanticbase.yml](deploy/config-server/semanticbase.yml)
  directly instead of a config server, starts the app and runs `-Peval`. Scores appear in the job log; the app log and
  reports are attached as the `eval-results` artifact.
  - Needs the `LLM_API_KEY` repository secret, plus `EMBEDDING_API_KEY` when the template uses a hosted embedding provider.
  - The `delay-ms` input paces questions for free-tier rate limits (default 15 s, ~7 minutes per run).

## Data flow

- **What stays local:** extraction (Tika), PII redaction and storage (Postgres, Redis) run on your infrastructure. Original files never leave it.
- **What leaves:** with a hosted embedding provider, every redacted chunk is sent to it at ingestion, and each question at query time. With local Ollama, neither leaves. The chat provider receives the question and the top retrieved chunks (PII-redacted) at query time. With a hosted provider that text is processed on their infrastructure under their data policy; check whether a free tier trains on prompts before using real documents.
- For no third-party processing, use Ollama for embeddings and point `spring.ai.openai.base-url` at a vLLM server you control. Reaching it through a public tunnel such as ngrok still routes traffic through the tunnel provider.

## Development notes

- The pgvector dimension is fixed at 768. Moving to a model with a different output size requires a coordinated schema migration — keep this value in `application.yml`, not in the config server.
- The semantic cache threshold (`semanticbase.cache.semantic.threshold`) is tuned to 0.92 for `nomic-embed-text`; retune it when you switch embedding models (raise toward 0.97 for stronger ones).
