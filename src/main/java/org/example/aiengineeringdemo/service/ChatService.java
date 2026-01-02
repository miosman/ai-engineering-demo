package org.example.aiengineeringdemo.service;

import org.example.aiengineeringdemo.tools.WeatherTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient ragChatClient;
    private final ChatClient toolChatClient;
    private final WeatherTool weatherTool;

    public ChatService(
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            @Qualifier("toolChatClient") ChatClient toolChatClient,
            WeatherTool weatherTool) {
        this.ragChatClient = ragChatClient;
        this.toolChatClient = toolChatClient;
        this.weatherTool = weatherTool;
    }

    public String ragChat(String message) {
        return ragChatClient.prompt()
            .user(message)
            .call()
            .content();
    }

    public String toolChat(String message) {
        return toolChatClient.prompt()
            .user(message)
            .tools(weatherTool)
            .call()
            .content();
    }
}
