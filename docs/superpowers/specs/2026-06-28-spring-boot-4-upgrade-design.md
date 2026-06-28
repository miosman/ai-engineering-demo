# Spring Boot 4.1.0 + Spring AI 2.0.0 Upgrade Design

**Date:** 2026-06-28  
**Author:** Mohamed Osman  

## Overview

Upgrade the ai-engineering-demo project from Spring Boot 3.5.11 / Spring AI 1.1.3 to Spring Boot 4.1.0 / Spring AI 2.0.0. Both are major version bumps with known breaking changes. The work is split into two phases: establishing a comprehensive test baseline first, then performing the upgrade.

## Current State

- Spring Boot 3.5.11, Spring AI 1.1.3, Java 21
- 9 source files: `AiConfig`, `ChatService`, `DocumentIngestionService`, `WeatherTool`, two controllers, three DTOs
- One test: `AiEngineeringDemoApplicationTests.contextLoads()` — requires live LM Studio + PostgreSQL, no business logic coverage
- Key Spring AI surface area: `ChatClient`, `QuestionAnswerAdvisor`, `VectorStore`, `TikaDocumentReader`, `TokenTextSplitter`, `@Tool`, `@ToolParam`

## Approach

Option A — Sequential: complete and merge the test baseline before any upgrade bytes land on a branch. The test suite serves as the acceptance criteria for the upgrade; the upgrade is done when all tests are green.

## Branch Strategy

| Branch | Purpose |
|---|---|
| `test-baseline` | Add full unit + integration test suite; merge to `main` |
| `upgrade/spring-boot-4` | Bump versions, fix breaking changes; done when all tests pass |

## Phase 1: Test Baseline

### Test Structure

```
src/test/java/org/example/aiengineeringdemo/
├── unit/
│   ├── service/
│   │   ├── ChatServiceTest.java
│   │   └── DocumentIngestionServiceTest.java
│   ├── tools/
│   │   └── WeatherToolTest.java
│   └── controller/
│       ├── ChatControllerTest.java
│       └── DocumentControllerTest.java
└── integration/
    ├── AiEngineeringDemoApplicationTests.java  (updated in-place)
    └── VectorStoreIntegrationTest.java
```

### New Test Dependencies

```xml
<!-- Testcontainers PostgreSQL -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>

<!-- Spring Boot Testcontainers (@ServiceConnection) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>

<!-- WireMock for stubbing LM Studio HTTP endpoint -->
<dependency>
    <groupId>org.wiremock.integrations</groupId>
    <artifactId>wiremock-spring-boot</artifactId>
    <version>3.9.0</version>
    <scope>test</scope>
</dependency>
```

Testcontainers core and Mockito are already on the classpath via `spring-boot-starter-test`.

### Unit Test Coverage

| Test class | Mocks | Assertions |
|---|---|---|
| `ChatServiceTest` | `ChatClient`, `ChatClient.CallResponseSpec` | `ragChat()` calls `.prompt().user().call().content()`; `toolChat()` passes `weatherTool` to `.tools()` |
| `DocumentIngestionServiceTest` | `VectorStore` | `ingestDocument()` splits docs and calls `vectorStore.add()`; `listDocuments()` returns filenames; `clearAllDocuments()` empties the list |
| `WeatherToolTest` | `RestClient`, `RestClient.ResponseSpec` | City lookup hits correct coordinates; unknown city returns error string; coordinates path works; HTTP failure returns graceful message |
| `ChatControllerTest` | `ChatService` via `@WebMvcTest` | POST `/api/chat/rag` and `/api/chat/tools` return 200 with JSON; bad input returns 400 |
| `DocumentControllerTest` | `DocumentIngestionService` via `@WebMvcTest` | Upload returns chunk count; list returns filenames; delete returns 200 |

### Integration Test Coverage

| Test class | Infrastructure | Assertions |
|---|---|---|
| `AiEngineeringDemoApplicationTests` | `PostgreSQLContainer` via `@ServiceConnection` + WireMock on port 1234 | Full context loads; `VectorStore` bean exists and is healthy |
| `VectorStoreIntegrationTest` | Same container setup | Documents added to vector store are retrievable via similarity search; pgvector schema initialized correctly |

**Mocking rationale:** `@ServiceConnection` on a `PostgreSQLContainer` bean auto-configures `spring.datasource.*` — no manual property overrides. WireMock intercepts OpenAI-compatible HTTP calls so Spring AI auto-configuration wires cleanly without a live LM Studio process.

## Phase 2: Version Upgrade

### Step 1 — Version bump

Update `pom.xml`:
- Spring Boot parent: `3.5.11` → `4.1.0`
- Spring AI BOM: `1.1.3` → `2.0.0`

Run `./mvnw clean package -DskipTests` first to surface all compilation errors before touching logic.

### Step 2 — Artifact ID fixes

Spring AI 2.0.0 reorganized starter names. Verify/update each:
- `spring-ai-advisors-vector-store`
- `spring-ai-starter-model-openai`
- `spring-ai-starter-vector-store-pgvector`
- `spring-ai-tika-document-reader`

### Step 3 — Package/import fixes

Spring AI 2.0.0 moved several classes. Expected changes:
- `QuestionAnswerAdvisor` (currently `org.springframework.ai.chat.client.advisor.vectorstore`)
- `@Tool` / `@ToolParam` (currently `org.springframework.ai.tool.annotation`)
- `TokenTextSplitter` (currently `org.springframework.ai.transformer.splitter`)
- `TikaDocumentReader` (currently `org.springframework.ai.reader.tika`)

### Step 4 — API signature fixes

Fix any method signature or builder pattern changes revealed by compilation errors after import fixes.

### Step 5 — Property renames

Update `application.properties` for any renamed config keys in Spring Boot 4 or Spring AI 2.0 (e.g., `spring.ai.model.chat`, `spring.ai.vectorstore.pgvector.*`).

### Step 6 — Verify

Run the full test suite. The upgrade is complete when all Phase 1 tests pass green.

## Success Criteria

- Phase 1: `./mvnw test` passes with no external services (LM Studio or PostgreSQL on host) required
- Phase 2: `./mvnw test` still passes after version bump and all breaking-change fixes
