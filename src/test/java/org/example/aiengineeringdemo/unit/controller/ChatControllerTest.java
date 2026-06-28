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
