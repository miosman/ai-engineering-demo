package org.example.aiengineeringdemo.unit.controller;

import org.example.aiengineeringdemo.controller.DocumentController;
import org.example.aiengineeringdemo.service.DocumentIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @MockitoBean
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
