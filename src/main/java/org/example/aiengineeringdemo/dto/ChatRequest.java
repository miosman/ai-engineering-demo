package org.example.aiengineeringdemo.dto;

public record ChatRequest(
    String message,
    boolean useTools
) {}
