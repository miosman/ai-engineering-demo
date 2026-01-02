package org.example.aiengineeringdemo.controller;

import org.example.aiengineeringdemo.dto.DocumentUploadResponse;
import org.example.aiengineeringdemo.service.DocumentIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;

    public DocumentController(DocumentIngestionService documentIngestionService) {
        this.documentIngestionService = documentIngestionService;
    }

    @PostMapping("/upload")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(@RequestParam("file") MultipartFile file) {
        int chunksProcessed = documentIngestionService.ingestDocument(file);
        return ResponseEntity.ok(new DocumentUploadResponse(
            file.getOriginalFilename(),
            chunksProcessed,
            "Document processed successfully"
        ));
    }

    @GetMapping
    public ResponseEntity<List<String>> listDocuments() {
        return ResponseEntity.ok(documentIngestionService.listDocuments());
    }

    @DeleteMapping
    public ResponseEntity<Void> clearDocuments() {
        documentIngestionService.clearAllDocuments();
        return ResponseEntity.noContent().build();
    }
}
