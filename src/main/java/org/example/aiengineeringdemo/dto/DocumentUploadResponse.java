package org.example.aiengineeringdemo.dto;

public record DocumentUploadResponse(
    String filename,
    int chunksProcessed,
    String message
) {}
