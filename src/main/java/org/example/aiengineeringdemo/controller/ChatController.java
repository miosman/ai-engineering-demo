package org.example.aiengineeringdemo.controller;

import org.example.aiengineeringdemo.dto.ChatRequest;
import org.example.aiengineeringdemo.dto.ChatResponse;
import org.example.aiengineeringdemo.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/rag")
    public ResponseEntity<ChatResponse> ragChat(@RequestBody ChatRequest request) {
        String response = chatService.ragChat(request.message());
        return ResponseEntity.ok(new ChatResponse(response));
    }

    @PostMapping("/tools")
    public ResponseEntity<ChatResponse> toolChat(@RequestBody ChatRequest request) {
        String response = chatService.toolChat(request.message());
        return ResponseEntity.ok(new ChatResponse(response));
    }
}
