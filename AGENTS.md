# AGENTS.md

This file provides guidance to OpenAI Codex when working with code in this repository.

## Project Overview

AI Engineering Demo - a Spring Boot 4.1.0 application demonstrating Spring AI 2.0.0 integration with LM Studio (local LLM) and PGvector vector database. Features RAG (document Q&A) and tool calling (weather API). Uses Java 21 and Maven.

The app never calls a hosted LLM provider: Spring AI's **OpenAI** starter is pointed at LM Studio's OpenAI-compatible server on `localhost:1234`. No Anthropic/OpenAI API keys are involved (`spring.ai.openai.api-key=lm-studio` is a placeholder).

## Build & Run Commands

```bash
# Start PostgreSQL with pgvector
docker-compose up -d

# Run the application (needs LM Studio on :1234 and the pgvector container)
./mvnw spring-boot:run

# Run all tests — Docker must be running (Testcontainers pulls pgvector + WireMock images)
./mvnw test

# Unit tests only — no Docker required
./mvnw test -Dtest='**/unit/**/*Test'

./mvnw test -Dtest=TestClass         # Single test class
./mvnw test -Dtest=TestClass#method  # Single test method

# Build
./mvnw clean package
```

## Architecture

```
org.example.aiengineeringdemo/
├── config/
│   └── AiConfig.java              # ChatClient beans (ragChatClient, toolChatClient) + RestClientCustomizer
├── controller/
│   ├── ChatController.java        # /api/chat/rag, /api/chat/tools
│   └── DocumentController.java    # /api/documents/*
├── service/
│   ├── ChatService.java           # Orchestrates RAG and tool chat
│   └── DocumentIngestionService.java  # TikaDocumentReader + TokenTextSplitter
├── tools/
│   └── WeatherTool.java           # @Tool annotated weather service (Open-Meteo API)
└── dto/
    └── ChatRequest, ChatResponse, DocumentUploadResponse
```

Web UI is a single file, `src/main/resources/static/index.html` (vanilla JS, no build step). It pulls `marked` and `DOMPurify` from jsDelivr to render Markdown in chat responses.

## Key Spring AI Patterns

**RAG with QuestionAnswerAdvisor** (`AiConfig.java`):
```java
ChatClient.builder()
    .defaultSystem("...")
    .defaultAdvisors(
        QuestionAnswerAdvisor.builder(vectorStore)
            .searchRequest(SearchRequest.builder().topK(5).build())
            .build()
    )
    .build();
```

**Tool Calling** - tools are passed per-request in `ChatService.toolChat()`:
```java
toolChatClient.prompt()
    .user(message)
    .tools(weatherTool)  // WeatherTool instance with @Tool methods
    .call()
    .content();
```

**Document Ingestion** (`DocumentIngestionService`): Uses TikaDocumentReader (supports PDF, Word, etc.) and TokenTextSplitter before storing in PGvector. Each chunk gets `filename` and `contentType` metadata.

**Document listing is in-memory only.** `DocumentIngestionService` tracks uploaded filenames in an `ArrayList`; `GET /api/documents` reads that list and `DELETE /api/documents` only clears it. Neither touches pgvector - vectors persist in the `pgvector_data` Docker volume across restarts, while the filename list resets on every restart. RAG answers can therefore cite documents that no longer appear in the list.

## Spring Boot 4 / Spring AI 2.0.0 specifics

This branch (`upgrade/spring-boot-4`) is the result of a major-version upgrade; design and plan are in `docs/superpowers/specs/2026-06-28-spring-boot-4-upgrade-design.md` and `docs/superpowers/plans/`. Things that differ from Boot 3 / Spring AI 1.x code you may recall:

- **Jackson 3**: `ObjectMapper`/`JsonNode` live in `tools.jackson.databind`, not `com.fasterxml.jackson`.
- `RestClientCustomizer` is in `org.springframework.boot.restclient`.
- Tests use `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`), not `@MockBean`; MVC slice tests need the `spring-boot-webmvc-test` starter.
- Spring AI artifacts are `spring-ai-starter-model-openai`, `spring-ai-starter-vector-store-pgvector`, `spring-ai-vector-store-advisor`, `spring-ai-tika-document-reader` (BOM-managed via `spring-ai.version`).
- `AiConfig.restClientCustomizer` pins the JDK `HttpClient` to HTTP/1.1. It has been there since the initial commit with no recorded rationale - leave it in place.

## Configuration

Key settings in `application.properties`:
- LM Studio endpoint: `http://127.0.0.1:1234/v1` - **the `/v1` suffix is required.** Spring AI 2.0.0's OpenAI client appends `/chat/completions` and `/embeddings` to `base-url`; without `/v1`, LM Studio answers the wrong path with HTTP 200 + an error body, which surfaces as `OpenAIInvalidDataException: 'data' is not set`. `OpenAiClientSmokeTest` exists specifically to catch this regression.
- Chat model: `google/gemma-4-e4b`
- Embedding model: `text-embedding-granite-embedding-107m-multilingual`
- PGvector dimensions: `384` (must match embedding model output; tests hard-code 384-dim vectors)
- Vector similarity: COSINE_DISTANCE with HNSW index; `initialize-schema=true` creates the table on startup
- `logging.level.org.springframework.ai=DEBUG` is on - expect verbose request/response logs

## Testing

Three tiers, distinguished by what they mock:

| Tier | Tests | Spring context | Docker | Model beans |
|---|---|---|---|---|
| Unit (`unit/`) | `ChatServiceTest`, `DocumentIngestionServiceTest`, `WeatherToolTest`, `*ControllerTest` | None / `@WebMvcTest` | No | Plain Mockito; `ChatClient`'s fluent API is stubbed spec-by-spec (`prompt()` -> `ChatClientRequestSpec` -> `CallResponseSpec`) |
| Integration | `AiEngineeringDemoApplicationTests`, `VectorStoreIntegrationTest` | `@SpringBootTest` | Testcontainers `pgvector/pgvector:pg16` via `@ServiceConnection` | `ChatModel` / `EmbeddingModel` replaced with `@MockitoBean` |
| Smoke | `OpenAiClientSmokeTest` | `@SpringBootTest` | pgvector **+** `wiremock/wiremock:3.9.1` | **Real** `OpenAiChatModel` / `OpenAiEmbeddingModel` beans hitting the WireMock container |

Gotchas:
- A `@MockitoBean EmbeddingModel` never delegates default interface methods, so stubbing `call()` alone is not enough. `PgVectorStore` calls `embed(List<Document>, EmbeddingOptions, BatchingStrategy)` when adding and `embed(String)` when searching - stub both (see `VectorStoreIntegrationTest.stubEmbeddingModel`). The test sets `similarityThreshold(0.0)` explicitly; that is already the default, and because every stubbed vector is identical (cosine similarity ~1.0) results would match regardless.
- WireMock stubs live in `src/test/resources/wiremock/mappings/`. `chat-completions.json` and `embeddings.json` answer only on `/v1/...`; `zz-catch-all.json` (lowest priority) returns HTTP 200 with an error body to mimic LM Studio's wrong-path behaviour. Add a mapping there if the smoke test needs a new endpoint.
- `@ServiceConnection` overrides the `spring.datasource.*` properties in tests, so the Testcontainers database is always used regardless of `application.properties`.

## Development Requirements

- Java 21 (toolchain target; the wrapper runs fine on newer JDKs)
- Docker (for PostgreSQL/pgvector via docker-compose, and for Testcontainers in the test suite)
- LM Studio running on `localhost:1234` with a model loaded (must support embeddings)

**Weather tool supported cities**: New York, London, Tokyo, Paris, Sydney, Dubai, Singapore, Berlin, Los Angeles, San Francisco

## Git Conventions

- Use conventional commits when crafting commit messages (e.g., `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`)
- `CLAUDE.md` at the repo root is a copy of this file for Claude Code; keep the two in sync when editing either.
