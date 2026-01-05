# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI Engineering Demo - a Spring Boot 3.5.9 application demonstrating Spring AI 1.1.2 integration with LM Studio (local LLM) and PGvector vector database. Features RAG (document Q&A) and tool calling (weather API). Uses Java 21 and Maven.

## Build & Run Commands

```bash
# Start PostgreSQL with pgvector
docker-compose up -d

# Run the application
./mvnw spring-boot:run

# Run tests
./mvnw test
./mvnw test -Dtest=TestClass         # Single test class
./mvnw test -Dtest=TestClass#method  # Single test method

# Build
./mvnw clean package
```

## Architecture

```
org.example.aiengineeringdemo/
├── config/
│   └── AiConfig.java              # ChatClient beans (ragChatClient, toolChatClient)
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

**Document Ingestion** (`DocumentIngestionService`): Uses TikaDocumentReader (supports PDF, Word, etc.) and TokenTextSplitter before storing in PGvector.

## REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/chat/rag` | RAG-based document Q&A |
| POST | `/api/chat/tools` | Tool-enabled chat (weather) |
| POST | `/api/documents/upload` | Upload document for RAG |
| GET | `/api/documents` | List ingested documents |
| DELETE | `/api/documents` | Clear all documents |

Web UI available at `http://localhost:8080`

## Configuration

Key settings in `application.properties`:
- LM Studio endpoint: `localhost:1234` (OpenAI-compatible API)
- Chat model: `ibm/granite-4-h-tiny`
- Embedding model: `text-embedding-granite-embedding-107m-multilingual`
- PGvector dimensions: `384` (must match embedding model output)
- Vector similarity: COSINE_DISTANCE with HNSW index

## Development Requirements

- Java 21
- Docker (for PostgreSQL/pgvector via docker-compose)
- LM Studio running on `localhost:1234` with a model loaded (must support embeddings)

**Weather tool supported cities**: New York, London, Tokyo, Paris, Sydney, Dubai, Singapore, Berlin, Los Angeles, San Francisco

## Git Conventions

- Use conventional commits when crafting commit messages (e.g., `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`)
