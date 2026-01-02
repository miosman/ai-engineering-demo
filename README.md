# AI Engineering Demo

A Spring Boot application demonstrating Spring AI integration with LM Studio (local LLM) and PGvector vector database. Features RAG (Retrieval-Augmented Generation) for document Q&A and tool calling capabilities.

## Tech Stack

- Java 21
- Spring Boot 3.5.9
- Spring AI 1.1.2
- PostgreSQL with pgvector extension
- LM Studio (local LLM)

## Prerequisites

1. **Java 21** - Ensure JDK 21 is installed
2. **Docker** - Required for PostgreSQL/pgvector
3. **LM Studio** - Download from [lmstudio.ai](https://lmstudio.ai)

## Setup

### 1. Start LM Studio

1. Open LM Studio
2. Load a model that supports embeddings (e.g., `ibm/granite-4-h-tiny`)
3. Start the local server on `localhost:1234`

### 2. Start PostgreSQL with pgvector

```bash
docker-compose up -d
```

### 3. Run the Application

```bash
./mvnw spring-boot:run
```

The application will be available at `http://localhost:8080`

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/chat/rag` | RAG-based document Q&A |
| POST | `/api/chat/tools` | Tool-enabled chat (weather) |
| POST | `/api/documents/upload` | Upload document for RAG |
| GET | `/api/documents` | List ingested documents |
| DELETE | `/api/documents` | Clear all documents |

## Features

### RAG (Retrieval-Augmented Generation)

Upload documents (PDF, Word, etc.) and ask questions about their content. The system uses vector similarity search to find relevant context before generating responses.

### Tool Calling

Chat with the AI using natural language to get weather information. Supported cities: New York, London, Tokyo, Paris, Sydney, Dubai, Singapore, Berlin, Los Angeles, San Francisco.

## Development

```bash
# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=TestClass

# Run a single test method
./mvnw test -Dtest=TestClass#method

# Build
./mvnw clean package
```

## Configuration

Key settings can be modified in `src/main/resources/application.properties`:

- LM Studio endpoint: `localhost:1234`
- Chat model: `ibm/granite-4-h-tiny`
- Embedding model: `text-embedding-granite-embedding-107m-multilingual`
- PGvector dimensions: `384`
