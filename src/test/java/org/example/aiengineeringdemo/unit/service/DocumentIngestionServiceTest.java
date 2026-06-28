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
    void clearAllDocuments_emptiesInMemoryFilenameTracker() throws Exception {
        // Note: clearAllDocuments() only clears the in-memory filename tracker.
        // Embedded chunks in the VectorStore are NOT removed.
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
