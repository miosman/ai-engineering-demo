package org.example.aiengineeringdemo.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class VectorStoreIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @MockitoBean
    ChatModel chatModel;

    @MockitoBean
    EmbeddingModel embeddingModel;

    @Autowired
    VectorStore vectorStore;

    @BeforeEach
    void stubEmbeddingModel() {
        float[] vector = new float[384];
        Arrays.fill(vector, 0.1f);
        // Stub call() for any direct callers
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenAnswer(inv -> {
            EmbeddingRequest req = inv.getArgument(0);
            List<Embedding> embeddings = IntStream.range(0, req.getInstructions().size())
                .mapToObj(i -> new Embedding(vector, i))
                .toList();
            return new EmbeddingResponse(embeddings);
        });
        // Stub embed(documents, options, batchingStrategy) — the method PgVectorStore calls when adding docs.
        // @MockBean stubs all methods with default values so default interface methods are never
        // delegated; this explicit stub ensures the mock returns a proper embedding per document.
        when(embeddingModel.embed(anyList(), any(EmbeddingOptions.class), any(BatchingStrategy.class)))
            .thenAnswer(inv -> {
                List<Document> docs = inv.getArgument(0);
                return docs.stream().map(d -> vector).toList();
            });
        // Stub embed(String) — PgVectorStore calls this to embed the search query string.
        when(embeddingModel.embed(any(String.class))).thenReturn(vector);
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
