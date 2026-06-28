# Spring Boot 4.1.0 + Spring AI 2.0.0 Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish a comprehensive unit + integration test suite documenting current behavior on Spring Boot 3.5.11 / Spring AI 1.1.3, then upgrade to Spring Boot 4.1.0 / Spring AI 2.0.0 and fix all breaking changes until the full suite is green.

**Architecture:** Two sequential branches — `test-baseline` adds 7 test classes (5 unit, 2 integration) covering all production code; `upgrade/spring-boot-4` bumps versions and fixes breaking changes. Integration tests use a real `pgvector/pgvector:pg16` container via Testcontainers with `@MockBean ChatModel` and `@MockBean EmbeddingModel` — no LM Studio or host PostgreSQL required.

**Tech Stack:** Spring Boot 3.5.11 → 4.1.0, Spring AI 1.1.3 → 2.0.0, JUnit 5, Mockito 5, Testcontainers, Spring Boot Testcontainers (`@ServiceConnection`), AssertJ, MockMvc

---

## Phase 1: Test Baseline (branch: `test-baseline`)

### File Map

| Action | Path |
|--------|------|
| Modify | `pom.xml` |
| Modify | `src/test/java/org/example/aiengineeringdemo/AiEngineeringDemoApplicationTests.java` |
| Create | `src/test/java/org/example/aiengineeringdemo/integration/VectorStoreIntegrationTest.java` |
| Create | `src/test/java/org/example/aiengineeringdemo/unit/service/ChatServiceTest.java` |
| Create | `src/test/java/org/example/aiengineeringdemo/unit/service/DocumentIngestionServiceTest.java` |
| Create | `src/test/java/org/example/aiengineeringdemo/unit/tools/WeatherToolTest.java` |
| Create | `src/test/java/org/example/aiengineeringdemo/unit/controller/ChatControllerTest.java` |
| Create | `src/test/java/org/example/aiengineeringdemo/unit/controller/DocumentControllerTest.java` |

---

### Task 1: Create test-baseline branch

- [ ] **Step 1: Create and switch to the branch**

```bash
git checkout -b test-baseline
```

Expected: `Switched to a new branch 'test-baseline'`

---

### Task 2: Add test dependencies

**Files:** Modify `pom.xml`

- [ ] **Step 1: Add Testcontainers dependencies inside `<dependencies>`, after `spring-boot-starter-test`**

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Verify the build resolves the new dependencies**

```bash
./mvnw dependency:resolve -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "test: add Testcontainers and spring-boot-testcontainers dependencies"
```

---

### Task 3: Write ChatControllerTest

**Files:**
- Create: `src/test/java/org/example/aiengineeringdemo/unit/controller/ChatControllerTest.java`

- [ ] **Step 1: Create the test file**

```java
package org.example.aiengineeringdemo.unit.controller;

import org.example.aiengineeringdemo.controller.ChatController;
import org.example.aiengineeringdemo.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ChatService chatService;

    @Test
    void ragChat_returnsResponseJson() throws Exception {
        when(chatService.ragChat("hello")).thenReturn("rag answer");

        mockMvc.perform(post("/api/chat/rag")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hello\",\"useTools\":false}"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.response").value("rag answer"));
    }

    @Test
    void toolChat_returnsResponseJson() throws Exception {
        when(chatService.toolChat("what's the weather?")).thenReturn("It's sunny");

        mockMvc.perform(post("/api/chat/tools")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"what's the weather?\",\"useTools\":true}"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.response").value("It's sunny"));
    }

    @Test
    void ragChat_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/chat/rag")
                .contentType(MediaType.APPLICATION_JSON)
                .content("not-json"))
            .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run the tests**

```bash
./mvnw test -Dtest=ChatControllerTest 2>&1 | tail -10
```

Expected: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/example/aiengineeringdemo/unit/controller/ChatControllerTest.java
git commit -m "test: add ChatControllerTest with MockMvc for rag and tool endpoints"
```

---

### Task 4: Write DocumentControllerTest

**Files:**
- Create: `src/test/java/org/example/aiengineeringdemo/unit/controller/DocumentControllerTest.java`

- [ ] **Step 1: Create the test file**

```java
package org.example.aiengineeringdemo.unit.controller;

import org.example.aiengineeringdemo.controller.DocumentController;
import org.example.aiengineeringdemo.service.DocumentIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    DocumentIngestionService documentIngestionService;

    @Test
    void uploadDocument_returnsFilenameAndChunkCount() throws Exception {
        when(documentIngestionService.ingestDocument(any())).thenReturn(5);

        MockMultipartFile file = new MockMultipartFile(
            "file", "report.pdf", "application/pdf", "pdf content".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.filename").value("report.pdf"))
            .andExpect(jsonPath("$.chunksProcessed").value(5))
            .andExpect(jsonPath("$.message").value("Document processed successfully"));
    }

    @Test
    void listDocuments_returnsFilenameList() throws Exception {
        when(documentIngestionService.listDocuments()).thenReturn(List.of("a.pdf", "b.txt"));

        mockMvc.perform(get("/api/documents"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").value("a.pdf"))
            .andExpect(jsonPath("$[1]").value("b.txt"));
    }

    @Test
    void clearDocuments_returns204AndDelegates() throws Exception {
        mockMvc.perform(delete("/api/documents"))
            .andExpect(status().isNoContent());

        verify(documentIngestionService).clearAllDocuments();
    }
}
```

- [ ] **Step 2: Run the tests**

```bash
./mvnw test -Dtest=DocumentControllerTest 2>&1 | tail -10
```

Expected: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/example/aiengineeringdemo/unit/controller/DocumentControllerTest.java
git commit -m "test: add DocumentControllerTest for upload, list, and delete endpoints"
```

---

### Task 5: Write ChatServiceTest

**Files:**
- Create: `src/test/java/org/example/aiengineeringdemo/unit/service/ChatServiceTest.java`

- [ ] **Step 1: Create the test file**

```java
package org.example.aiengineeringdemo.unit.service;

import org.example.aiengineeringdemo.service.ChatService;
import org.example.aiengineeringdemo.tools.WeatherTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    ChatClient ragChatClient;

    @Mock
    ChatClient toolChatClient;

    @Mock
    WeatherTool weatherTool;

    ChatService chatService;

    @BeforeEach
    void setUp() {
        // Construct manually to control which mock goes to which qualifier slot
        chatService = new ChatService(ragChatClient, toolChatClient, weatherTool);
    }

    @Test
    void ragChat_callsRagClientAndReturnsContent() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        when(ragChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user("tell me about RAG")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("RAG is retrieval augmented generation");

        String result = chatService.ragChat("tell me about RAG");

        assertThat(result).isEqualTo("RAG is retrieval augmented generation");
        verify(ragChatClient).prompt();
        verifyNoInteractions(toolChatClient);
    }

    @Test
    void toolChat_passesWeatherToolToClientAndReturnsContent() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        when(toolChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user("What is the weather in London?")).thenReturn(requestSpec);
        when(requestSpec.tools(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("It is 22°C and partly cloudy in London.");

        String result = chatService.toolChat("What is the weather in London?");

        assertThat(result).isEqualTo("It is 22°C and partly cloudy in London.");
        verify(requestSpec).tools(weatherTool);
        verifyNoInteractions(ragChatClient);
    }
}
```

- [ ] **Step 2: Run the tests**

```bash
./mvnw test -Dtest=ChatServiceTest 2>&1 | tail -10
```

Expected: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/example/aiengineeringdemo/unit/service/ChatServiceTest.java
git commit -m "test: add ChatServiceTest verifying rag and tool chat client delegation"
```

---

### Task 6: Write DocumentIngestionServiceTest

**Files:**
- Create: `src/test/java/org/example/aiengineeringdemo/unit/service/DocumentIngestionServiceTest.java`

- [ ] **Step 1: Create the test file**

```java
package org.example.aiengineeringdemo.unit.service;

import org.example.aiengineeringdemo.service.DocumentIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    VectorStore vectorStore;

    DocumentIngestionService service;

    @BeforeEach
    void setUp() {
        service = new DocumentIngestionService(vectorStore);
    }

    @Test
    void ingestDocument_storesChunksInVectorStore() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "article.txt", "text/plain",
            "Spring AI makes it easy to build AI-powered applications on the JVM.".getBytes()
        );

        int chunks = service.ingestDocument(file);

        assertThat(chunks).isGreaterThan(0);
        verify(vectorStore).add(anyList());
    }

    @Test
    void ingestDocument_tracksFilenameInList() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "my-report.txt", "text/plain", "report content here".getBytes()
        );

        service.ingestDocument(file);

        assertThat(service.listDocuments()).containsExactly("my-report.txt");
    }

    @Test
    void listDocuments_returnsEmptyListInitially() {
        assertThat(service.listDocuments()).isEmpty();
    }

    @Test
    void listDocuments_returnsDefensiveCopy() throws Exception {
        service.ingestDocument(new MockMultipartFile(
            "file", "doc.txt", "text/plain", "content".getBytes()
        ));

        service.listDocuments().clear(); // mutating the returned list must not affect internal state

        assertThat(service.listDocuments()).hasSize(1);
    }

    @Test
    void clearAllDocuments_emptiesFilenameList() throws Exception {
        service.ingestDocument(new MockMultipartFile(
            "file", "a.txt", "text/plain", "content a".getBytes()
        ));
        service.ingestDocument(new MockMultipartFile(
            "file", "b.txt", "text/plain", "content b".getBytes()
        ));
        assertThat(service.listDocuments()).hasSize(2);

        service.clearAllDocuments();

        assertThat(service.listDocuments()).isEmpty();
    }
}
```

- [ ] **Step 2: Run the tests**

```bash
./mvnw test -Dtest=DocumentIngestionServiceTest 2>&1 | tail -10
```

Expected: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/example/aiengineeringdemo/unit/service/DocumentIngestionServiceTest.java
git commit -m "test: add DocumentIngestionServiceTest covering ingest, list, clear, and defensive copy"
```

---

### Task 7: Write WeatherToolTest

**Files:**
- Create: `src/test/java/org/example/aiengineeringdemo/unit/tools/WeatherToolTest.java`

- [ ] **Step 1: Create the test file**

```java
package org.example.aiengineeringdemo.unit.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.aiengineeringdemo.tools.WeatherTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class WeatherToolTest {

    @Mock
    RestClient.Builder restClientBuilder;

    @Mock
    RestClient restClient;

    @Mock
    RestClient.RequestHeadersUriSpec requestSpec;

    @Mock
    RestClient.ResponseSpec responseSpec;

    WeatherTool weatherTool;

    // Matches the JSON structure returned by Open-Meteo
    private static final String WEATHER_JSON = """
        {
          "current": {
            "temperature_2m": 22.5,
            "relative_humidity_2m": 65.0,
            "wind_speed_10m": 15.3,
            "weather_code": 1
          }
        }
        """;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);
        when(restClient.get()).thenReturn(requestSpec);
        // uri(String, Object...) with two double args autoboxed to Double
        doReturn(requestSpec).when(requestSpec).uri(anyString(), any(), any());
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(WEATHER_JSON);

        weatherTool = new WeatherTool(restClientBuilder, new ObjectMapper());
    }

    @Test
    void getWeatherByCity_knownCity_returnsFormattedWeather() {
        String result = weatherTool.getWeatherByCity("London");

        assertThat(result).contains("London");
        assertThat(result).contains("22.5°C");
        assertThat(result).contains("65%");
        assertThat(result).contains("15.3 km/h");
        assertThat(result).contains("Partly cloudy");
    }

    @Test
    void getWeatherByCity_unknownCity_returnsErrorWithSupportedCitiesList() {
        String result = weatherTool.getWeatherByCity("Atlantis");

        assertThat(result).contains("Weather data not available for city: Atlantis");
        assertThat(result).contains("Supported cities:");
        verifyNoInteractions(restClient);
    }

    @Test
    void getWeatherByCoordinates_callsApiAndFormatsCoordinates() {
        String result = weatherTool.getWeatherByCoordinates(48.8566, 2.3522);

        assertThat(result).contains("48.86");
        assertThat(result).contains("2.35");
        verify(restClient).get();
    }

    @Test
    void getWeatherByCity_caseInsensitiveLookup_findsCity() {
        String result = weatherTool.getWeatherByCity("NEW YORK");

        // Original casing is preserved in the output; unknown city would skip the API call
        assertThat(result).contains("NEW YORK");
        verify(restClient).get();
    }

    @Test
    void getWeatherByCity_httpFailure_returnsGracefulErrorMessage() {
        when(responseSpec.body(String.class)).thenThrow(new RuntimeException("Connection refused"));

        String result = weatherTool.getWeatherByCity("London");

        assertThat(result).startsWith("Failed to fetch weather data:");
    }
}
```

- [ ] **Step 2: Run the tests**

```bash
./mvnw test -Dtest=WeatherToolTest 2>&1 | tail -10
```

Expected: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`

If the `doReturn(...).when(requestSpec).uri(anyString(), any(), any())` stub doesn't match the vararg call, try replacing with:
```java
doReturn(requestSpec).when(requestSpec).uri(anyString(), any(Object[].class));
```

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/example/aiengineeringdemo/unit/tools/WeatherToolTest.java
git commit -m "test: add WeatherToolTest for city lookup, coordinate format, and error handling"
```

---

### Task 8: Update AiEngineeringDemoApplicationTests (integration)

**Files:**
- Modify: `src/test/java/org/example/aiengineeringdemo/AiEngineeringDemoApplicationTests.java`

- [ ] **Step 1: Replace the entire file content**

```java
package org.example.aiengineeringdemo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class AiEngineeringDemoApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @MockBean
    ChatModel chatModel;

    @MockBean
    EmbeddingModel embeddingModel;

    @BeforeEach
    void stubEmbeddingModel() {
        float[] vector = new float[384];
        Arrays.fill(vector, 0.1f);
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenAnswer(inv -> {
            EmbeddingRequest req = inv.getArgument(0);
            List<Embedding> embeddings = IntStream.range(0, req.getInstructions().size())
                .mapToObj(i -> new Embedding(vector, i))
                .toList();
            return new EmbeddingResponse(embeddings);
        });
    }

    @Test
    void contextLoads() {
    }

    @Test
    void vectorStoreBeanIsAvailable(@Autowired ApplicationContext context) {
        assertThat(context.getBean(VectorStore.class)).isNotNull();
    }
}
```

- [ ] **Step 2: Run the test (Docker must be running)**

```bash
./mvnw test -Dtest=AiEngineeringDemoApplicationTests 2>&1 | tail -20
```

Expected: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`

First run pulls `pgvector/pgvector:pg16` from Docker Hub — may take a minute.

If the `Embedding(float[], int)` constructor is unavailable in Spring AI 1.1.3, check the actual constructor with:
```bash
./mvnw test-compile 2>&1 | grep "Embedding"
```
Then adjust to match the actual Spring AI 1.1.3 API (e.g., builder pattern or different arg types).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/example/aiengineeringdemo/AiEngineeringDemoApplicationTests.java
git commit -m "test: update context load test to use Testcontainers pgvector and mocked AI models"
```

---

### Task 9: Write VectorStoreIntegrationTest

**Files:**
- Create: `src/test/java/org/example/aiengineeringdemo/integration/VectorStoreIntegrationTest.java`

- [ ] **Step 1: Create the test file**

```java
package org.example.aiengineeringdemo.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class VectorStoreIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @MockBean
    ChatModel chatModel;

    @MockBean
    EmbeddingModel embeddingModel;

    @Autowired
    VectorStore vectorStore;

    @BeforeEach
    void stubEmbeddingModel() {
        float[] vector = new float[384];
        Arrays.fill(vector, 0.1f);
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenAnswer(inv -> {
            EmbeddingRequest req = inv.getArgument(0);
            List<Embedding> embeddings = IntStream.range(0, req.getInstructions().size())
                .mapToObj(i -> new Embedding(vector, i))
                .toList();
            return new EmbeddingResponse(embeddings);
        });
    }

    @Test
    void pgvectorSchemaInitializesAndAcceptsDocuments() {
        assertThatCode(() -> vectorStore.add(List.of(
            new Document("schema init test", Map.of("filename", "init.txt"))
        ))).doesNotThrowAnyException();
    }

    @Test
    void addedDocumentsAreRetrievableViaSimilaritySearch() {
        Document doc = new Document(
            "The quick brown fox jumps over the lazy dog",
            Map.of("filename", "fox.txt")
        );
        vectorStore.add(List.of(doc));

        // All stored vectors are identical (0.1f * 384), so all stored docs are equidistant.
        // similarityThreshold(0.0) ensures results are returned regardless.
        List<Document> results = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query("fox")
                .topK(5)
                .similarityThreshold(0.0)
                .build()
        );

        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(d -> d.getText().contains("quick brown fox"));
    }
}
```

- [ ] **Step 2: Run the test**

```bash
./mvnw test -Dtest=VectorStoreIntegrationTest 2>&1 | tail -20
```

Expected: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`

Spring Boot caches the test context, so the PostgreSQL container from Task 8 is reused — no second Docker pull.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/example/aiengineeringdemo/integration/VectorStoreIntegrationTest.java
git commit -m "test: add VectorStoreIntegrationTest with Testcontainers pgvector and mocked embeddings"
```

---

### Task 10: Run full suite and merge to main

- [ ] **Step 1: Run the complete test suite**

```bash
./mvnw test 2>&1 | tail -20
```

Expected output includes a line like:
```
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

If any test fails, fix it before proceeding. Do not merge a broken baseline.

- [ ] **Step 2: Merge to main**

```bash
git checkout main
git merge --no-ff test-baseline -m "feat: add comprehensive test baseline (unit + Testcontainers integration)"
```

- [ ] **Step 3: Confirm tests pass on main**

```bash
./mvnw test 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

---

## Phase 2: Version Upgrade (branch: `upgrade/spring-boot-4`)

### File Map

| Action | Path |
|--------|------|
| Modify | `pom.xml` |
| Modify | `src/main/java/org/example/aiengineeringdemo/config/AiConfig.java` |
| Modify | `src/main/java/org/example/aiengineeringdemo/service/DocumentIngestionService.java` |
| Modify | `src/main/java/org/example/aiengineeringdemo/tools/WeatherTool.java` |
| Modify | `src/main/resources/application.properties` |
| Possibly modify | `src/test/java/org/example/aiengineeringdemo/AiEngineeringDemoApplicationTests.java` |
| Possibly modify | `src/test/java/org/example/aiengineeringdemo/integration/VectorStoreIntegrationTest.java` |

---

### Task 11: Create upgrade branch and bump versions

**Files:** Modify `pom.xml`

- [ ] **Step 1: Create the upgrade branch**

```bash
git checkout -b upgrade/spring-boot-4
```

Expected: `Switched to a new branch 'upgrade/spring-boot-4'`

- [ ] **Step 2: Update version numbers in pom.xml**

Change the Spring Boot parent version (line 8):
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.0</version>
    <relativePath/>
</parent>
```

Change the Spring AI version property (line 18):
```xml
<properties>
    <java.version>21</java.version>
    <spring-ai.version>2.0.0</spring-ai.version>
</properties>
```

- [ ] **Step 3: Add Spring milestone + snapshot repositories (inside `<project>`, after `<build>`)**

Spring Boot 4.x and Spring AI 2.0.0 may not be in Maven Central — add:
```xml
<repositories>
    <repository>
        <id>spring-milestones</id>
        <name>Spring Milestones</name>
        <url>https://repo.spring.io/milestone</url>
        <snapshots><enabled>false</enabled></snapshots>
    </repository>
    <repository>
        <id>spring-snapshots</id>
        <name>Spring Snapshots</name>
        <url>https://repo.spring.io/snapshot</url>
        <releases><enabled>false</enabled></releases>
    </repository>
</repositories>
<pluginRepositories>
    <pluginRepository>
        <id>spring-milestones</id>
        <name>Spring Milestones</name>
        <url>https://repo.spring.io/milestone</url>
        <snapshots><enabled>false</enabled></snapshots>
    </pluginRepository>
</pluginRepositories>
```

- [ ] **Step 4: Test dependency resolution**

```bash
./mvnw dependency:resolve 2>&1 | grep -E "ERROR|Cannot|not found|BUILD" | head -20
```

If you see `Could not resolve` errors for Spring AI artifacts, proceed to Task 12 to fix artifact IDs. If resolution succeeds, skip Task 12 and go directly to Task 13.

- [ ] **Step 5: Commit**

```bash
git add pom.xml
git commit -m "chore: bump Spring Boot to 4.1.0 and Spring AI to 2.0.0"
```

---

### Task 12: Fix artifact resolution errors

**Files:** Modify `pom.xml`

Only needed if Task 11 Step 4 showed resolution errors for Spring AI artifacts.

- [ ] **Step 1: Discover available Spring AI 2.0.0 artifact IDs**

```bash
./mvnw dependency:resolve 2>&1 | grep -i "could not resolve\|not found" | grep "springframework.ai"
```

Cross-reference with the Spring AI 2.0.0 release notes on GitHub (`spring-projects/spring-ai`) and the published BOM. Known likely renames (verify each):

| 1.x artifact | Likely 2.0.0 artifact |
|---|---|
| `spring-ai-advisors-vector-store` | merged into `spring-ai-core` or renamed |
| `spring-ai-starter-model-openai` | `spring-ai-openai-spring-boot-starter` |
| `spring-ai-starter-vector-store-pgvector` | `spring-ai-pgvector-store-spring-boot-starter` |
| `spring-ai-tika-document-reader` | likely unchanged, verify |

- [ ] **Step 2: Update each failing Spring AI dependency in pom.xml to the correct 2.0.0 artifact ID**

For each `<artifactId>` that failed resolution, replace it with the verified 2.0.0 name from Step 1.

- [ ] **Step 3: Verify all dependencies resolve**

```bash
./mvnw dependency:resolve -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "chore: update Spring AI artifact IDs to 2.0.0 names"
```

---

### Task 13: Fix compilation errors (imports and API changes)

**Files:** `AiConfig.java`, `DocumentIngestionService.java`, `WeatherTool.java`, test files as needed

- [ ] **Step 1: Attempt compilation and capture all errors**

```bash
./mvnw compile 2>&1 | grep "error:" | head -50
```

- [ ] **Step 2: Fix QuestionAnswerAdvisor import in AiConfig.java**

If the import `org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor` fails, find the new package:
```bash
find ~/.m2/repository/org/springframework/ai -name "*.jar" | xargs -I{} jar tf {} 2>/dev/null | grep "QuestionAnswerAdvisor" | head -5
```

Update the import in `AiConfig.java` to the package reported by the above command.

- [ ] **Step 3: Fix TikaDocumentReader and TokenTextSplitter imports in DocumentIngestionService.java**

```bash
find ~/.m2/repository/org/springframework/ai -name "*.jar" | xargs -I{} jar tf {} 2>/dev/null | grep -E "TikaDocumentReader|TokenTextSplitter" | head -5
```

Update imports in `DocumentIngestionService.java` to the new package paths.

- [ ] **Step 4: Fix @Tool and @ToolParam imports in WeatherTool.java**

```bash
find ~/.m2/repository/org/springframework/ai -name "*.jar" | xargs -I{} jar tf {} 2>/dev/null | grep "Tool" | grep "annotation" | head -5
```

Update imports in `WeatherTool.java` to the new package paths.

- [ ] **Step 5: Fix any remaining API method signature changes**

For any error that remains after import fixes (e.g., method not found, wrong return type, changed builder API), read the Stack trace and look up the Spring AI 2.0.0 Javadoc or changelog for the correct call. Common areas:
- `SearchRequest.builder()` chain may have changed
- `QuestionAnswerAdvisor.builder()` chain may have changed
- `TokenTextSplitter` constructor may have changed

- [ ] **Step 6: Verify main sources compile cleanly**

```bash
./mvnw compile 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 7: Compile test sources**

```bash
./mvnw test-compile 2>&1 | grep "error:" | head -20
```

If test files fail due to moved types (`EmbeddingRequest`, `EmbeddingResponse`, `Embedding`, `ChatModel`), update their imports using the same `jar tf` lookup approach from Steps 2–4.

- [ ] **Step 8: Commit**

```bash
git add src/
git commit -m "fix: update Spring AI imports and API calls for 2.0.0 compatibility"
```

---

### Task 14: Fix application.properties renames

**Files:** `src/main/resources/application.properties`

- [ ] **Step 1: Start the Spring context via the integration test to surface property warnings**

```bash
./mvnw test -Dtest=AiEngineeringDemoApplicationTests 2>&1 | grep -E "Unrecognized|deprecated|No property|WARN" | head -20
```

- [ ] **Step 2: Fix each reported property**

Common Spring Boot 4.x and Spring AI 2.0.0 property renames to check:
- `spring.ai.model.chat` — may be removed or renamed
- `spring.ai.openai.base-url` — prefix likely stable, but verify
- `spring.ai.vectorstore.pgvector.*` keys — verify each key exists in 2.0.0

For each `Unrecognized field` or deprecation warning, replace the old key with the new one in `application.properties`.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/application.properties
git commit -m "fix: update application.properties keys for Spring Boot 4 and Spring AI 2.0.0"
```

---

### Task 15: Run full test suite, fix failures, open PR

- [ ] **Step 1: Run the complete test suite**

```bash
./mvnw test 2>&1 | tail -30
```

- [ ] **Step 2: Fix any remaining test failures**

For each failing test:
- If the failure is a compilation error, go back to Task 13
- If the failure is an assertion error, the production code behavior changed — read the error, check what Spring AI 2.0.0 changed, and update the mock setup in the test to match the new method signatures or return types

Repeat until:
```bash
./mvnw test 2>&1 | grep -E "Tests run|BUILD"
```
shows all tests passing and `BUILD SUCCESS`.

- [ ] **Step 3: Commit final state if any test-side fixes were needed**

```bash
git add src/
git status
git commit -m "fix: update test mocks for Spring AI 2.0.0 API changes"
```

- [ ] **Step 4: Open the pull request**

```bash
gh pr create \
  --title "feat: upgrade to Spring Boot 4.1.0 and Spring AI 2.0.0" \
  --body "$(cat <<'EOF'
## Summary
- Bumps Spring Boot from 3.5.11 to 4.1.0
- Bumps Spring AI from 1.1.3 to 2.0.0
- Fixes all breaking changes: artifact IDs, package renames, API changes, property renames
- All 20+ tests from the test-baseline pass against the upgraded dependencies

## Test plan
- [ ] Unit tests pass without external services: ChatControllerTest, DocumentControllerTest, ChatServiceTest, DocumentIngestionServiceTest, WeatherToolTest
- [ ] Integration tests pass with Testcontainers pgvector: AiEngineeringDemoApplicationTests, VectorStoreIntegrationTest
- [ ] No LM Studio or host PostgreSQL required to run `./mvnw test`
EOF
)" \
  --base main \
  --head upgrade/spring-boot-4
```
