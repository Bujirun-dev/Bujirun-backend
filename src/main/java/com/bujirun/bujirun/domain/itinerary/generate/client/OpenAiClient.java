package com.bujirun.bujirun.domain.itinerary.generate.client;

import com.bujirun.bujirun.domain.itinerary.generate.exception.OpenAiApiException;
import com.bujirun.bujirun.domain.itinerary.generate.exception.OpenAiRateLimitException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OpenAiClient {

    private final WebClient webClient;
    private final String model;

    public OpenAiClient(@Value("${openai.api.key}") String apiKey,
                        @Value("${openai.api.model:gpt-4.1-mini}") String model) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.model = model;
    }

    /**
     * OpenAI API 호출 - 프롬프트를 받아 텍스트 응답 반환
     */
    public String chat(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.7,
                "max_tokens", 8000,
                "response_format", Map.of("type", "json_object")
        );

        JsonNode response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.value() == 429 || status.value() == 413,
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(errorBody -> {
                                    log.warn("OpenAI rate limit 응답 - status: {}, body: {}",
                                            clientResponse.statusCode(), errorBody);
                                    return Mono.error(new OpenAiRateLimitException(
                                            "OpenAI rate limit exceeded: " + clientResponse.statusCode()));
                                }))
                .bodyToMono(JsonNode.class)
                .retryWhen(
                        Retry.backoff(2, Duration.ofSeconds(2))
                                .maxBackoff(Duration.ofSeconds(10))
                                .filter(ex -> ex instanceof OpenAiRateLimitException)
                                .doBeforeRetry(signal ->
                                        log.warn("OpenAI API 재시도 {}회차", signal.totalRetries() + 1))
                                .onRetryExhaustedThrow((spec, signal) ->
                                        new OpenAiRateLimitException("OpenAI API 재시도 모두 실패", signal.failure()))
                )
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.error("OpenAI API 호출 실패 - status: {}, body: {}",
                            ex.getStatusCode(), ex.getResponseBodyAsString());
                    return Mono.error(new OpenAiApiException("OpenAI API 호출 중 오류가 발생했습니다.", ex));
                })
                .block();

        if (response == null) {
            throw new OpenAiApiException("OpenAI API 응답이 비어있습니다.", null);
        }

        return response
                .path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();
    }
}