package org.example.aiengineeringdemo.integration;

import org.example.aiengineeringdemo.service.ChatService;
import org.example.aiengineeringdemo.service.DocumentIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end smoke test that exercises the <em>real</em> Spring AI OpenAI client beans
 * (no {@code @MockitoBean}) against a stubbed OpenAI-compatible server, backed by a real
 * pgvector database.
 *
 * <p>Why this exists: every other test mocks {@code ChatModel}/{@code EmbeddingModel}, so the
 * actual HTTP request path the OpenAI client builds is never validated. The Spring Boot 4 /
 * Spring AI 2.0.0 upgrade silently broke this — the new openai-java SDK appends {@code /embeddings}
 * (and {@code /chat/completions}) to {@code spring.ai.openai.base-url}, so the base-url must end in
 * {@code /v1}. With the old (pre-{@code /v1}) value, every call hit the wrong path and LM Studio
 * returned an error body with HTTP 200, surfacing as
 * {@code OpenAIInvalidDataException: 'data' is not set}.
 *
 * <p>The WireMock container only answers on {@code /v1/embeddings} and {@code /v1/chat/completions};
 * a catch-all reproduces LM Studio's "wrong path" response (HTTP 200 + error body, no {@code data}).
 * So if {@code base-url} ever loses its {@code /v1} suffix again, this test fails with the same
 * exception users would see in production.
 */
@SpringBootTest
@Testcontainers
class OpenAiClientSmokeTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Container
    static GenericContainer<?> openAiStub = new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.9.1"))
        .withExposedPorts(8080)
        .withClasspathResourceMapping("wiremock/mappings", "/home/wiremock/mappings",
            org.testcontainers.containers.BindMode.READ_ONLY);

    @DynamicPropertySource
    static void openAiProperties(DynamicPropertyRegistry registry) {
        // Point the real OpenAI client at the stub, with the SAME /v1 suffix production uses.
        String baseUrl = "http://" + openAiStub.getHost() + ":" + openAiStub.getMappedPort(8080) + "/v1";
        registry.add("spring.ai.openai.base-url", () -> baseUrl);
    }

    @Autowired
    DocumentIngestionService documentIngestionService;

    @Autowired
    ChatService chatService;

    @Test
    void ingestion_reachesEmbeddingEndpoint_andStoresChunks() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "smoke.txt", "text/plain",
            "Spring AI talks to LM Studio over an OpenAI-compatible API.".getBytes()
        );

        // Drives the real OpenAiEmbeddingModel -> POST {base-url}/embeddings -> pgvector.
        int chunks = documentIngestionService.ingestDocument(file);

        assertThat(chunks).isGreaterThan(0);
        assertThat(documentIngestionService.listDocuments()).contains("smoke.txt");
    }

    @Test
    void toolChat_reachesChatCompletionsEndpoint_andReturnsContent() {
        // Drives the real OpenAiChatModel -> POST {base-url}/chat/completions.
        String response = chatService.toolChat("ping");

        assertThat(response).isEqualTo("smoke-test-ok");
    }
}
