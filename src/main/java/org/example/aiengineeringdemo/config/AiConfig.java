package org.example.aiengineeringdemo.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient ragChatClient(ChatClient.Builder builder, VectorStore vectorStore) {
        return builder
            .defaultSystem("You are a helpful assistant. Use the provided context to answer questions accurately. If the context doesn't contain relevant information, say so.")
            .defaultAdvisors(
                QuestionAnswerAdvisor.builder(vectorStore)
                    .searchRequest(SearchRequest.builder().topK(5).build())
                    .build()
            )
            .build();
    }

    @Bean
    public ChatClient toolChatClient(ChatClient.Builder builder) {
        return builder
            .defaultSystem("You are a helpful assistant with access to external tools. Use the available tools when appropriate to answer user questions.")
            .build();
    }

    @Bean
    public RestClientCustomizer restClientCustomizer() {
        return restClientBuilder -> {
            HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
            restClientBuilder.requestFactory(new JdkClientHttpRequestFactory(httpClient));
        };
    }
}
