package com.bujirun.bujirun.domain.itinerary.generate.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GroqClient {

    private final WebClient webClient;

    public GroqClient(@Value("${groq.api.key}") String apiKey) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.groq.com/openai/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Groq API 호출 - 프롬프트를 받아 텍스트 응답 반환
     */
    public String chat(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", "llama-3.3-70b-versatile",
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.7,
                "max_tokens", 4000
        );

        JsonNode response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return response
                .path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();
    }
}