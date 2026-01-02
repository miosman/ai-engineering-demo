package org.example.aiengineeringdemo.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;
    private final List<String> ingestedDocuments = new ArrayList<>();

    public DocumentIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.textSplitter = new TokenTextSplitter();
    }

    public int ingestDocument(MultipartFile file) {
        try {
            InputStreamResource resource = new InputStreamResource(file.getInputStream());
            TikaDocumentReader reader = new TikaDocumentReader(resource);

            List<Document> documents = reader.read();

            documents.forEach(doc ->
                doc.getMetadata().putAll(Map.of(
                    "filename", file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown",
                    "contentType", file.getContentType() != null ? file.getContentType() : "unknown"
                ))
            );

            List<Document> chunks = textSplitter.split(documents);
            vectorStore.add(chunks);

            ingestedDocuments.add(file.getOriginalFilename());
            return chunks.size();
        } catch (IOException e) {
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
    }

    public List<String> listDocuments() {
        return new ArrayList<>(ingestedDocuments);
    }

    public void clearAllDocuments() {
        ingestedDocuments.clear();
    }
}
