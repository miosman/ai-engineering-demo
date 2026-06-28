package org.example.aiengineeringdemo.unit.service;

import org.example.aiengineeringdemo.service.ChatService;
import org.example.aiengineeringdemo.tools.WeatherTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    ChatClient ragChatClient;

    @Mock
    ChatClient toolChatClient;

    @Mock
    WeatherTool weatherTool;

    ChatService chatService;

    @BeforeEach
    void setUp() {
        // Construct manually to control which mock goes to which qualifier slot
        chatService = new ChatService(ragChatClient, toolChatClient, weatherTool);
    }

    @Test
    void ragChat_callsRagClientAndReturnsContent() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        when(ragChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user("tell me about RAG")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("RAG is retrieval augmented generation");

        String result = chatService.ragChat("tell me about RAG");

        assertThat(result).isEqualTo("RAG is retrieval augmented generation");
        verify(ragChatClient).prompt();
        verifyNoInteractions(toolChatClient);
    }

    @Test
    void toolChat_passesWeatherToolToClientAndReturnsContent() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        when(toolChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user("What is the weather in London?")).thenReturn(requestSpec);
        when(requestSpec.tools(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("It is 22°C and partly cloudy in London.");

        String result = chatService.toolChat("What is the weather in London?");

        assertThat(result).isEqualTo("It is 22°C and partly cloudy in London.");
        verify(requestSpec).tools(weatherTool);
        verifyNoInteractions(ragChatClient);
    }
}
