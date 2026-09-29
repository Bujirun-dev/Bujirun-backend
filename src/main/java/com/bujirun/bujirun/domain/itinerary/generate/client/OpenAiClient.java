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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OpenAiClient {

    private final WebClient webClient;
    private final String model;
    private final String reasoningEffort; // reasoning 모델(gpt-6 계열)용, 빈 값이면 모델 기본값

    public OpenAiClient(@Value("${openai.api.key}") String apiKey,
                        @Value("${openai.api.model:gpt-6-sol}") String model,
                        @Value("${openai.api.reasoning-effort:low}") String reasoningEffort) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.model = model;
        this.reasoningEffort = reasoningEffort.isBlank() ? null : reasoningEffort;
    }

    /**
     * OpenAI API 호출 - 프롬프트를 받아 JSON 응답 반환 (일정 생성/재최적화용, JSON 형식 강제)
     */
    public String chat(String systemPrompt, String userPrompt) {
        Map<String, Object> body = baseBody(systemPrompt, userPrompt, 0.7, 10000, 10000);
        body.put("response_format", Map.of("type", "json_object"));
        return call(body);
    }

    /**
     * OpenAI API 호출 - JSON 형식 강제 없이 순수 텍스트 응답 반환 (예: 소개글 요약)
     */
    public String chatPlainText(String systemPrompt, String userPrompt) {
        // reasoning 모델은 reasoning 토큰도 출력 한도에 포함되므로 요약문이 잘리지 않게 한도를 넉넉히 둔다
        Map<String, Object> body = baseBody(systemPrompt, userPrompt, 0.3, 500, 2000);
        return call(body);
    }

    /**
     * 모델 계열에 맞는 요청 body 생성
     * gpt-4 계열은 temperature/max_tokens를 쓰고, reasoning 모델(gpt-6 계열)은 max_tokens를 거부하고
     * temperature는 기본값(1)만 허용하므로 max_completion_tokens/reasoning_effort를 쓴다.
     */
    private Map<String, Object> baseBody(String systemPrompt, String userPrompt, double temperature,
                                         int maxTokens, int maxCompletionTokens) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        if (model.startsWith("gpt-4")) {
            body.put("temperature", temperature);
            body.put("max_tokens", maxTokens);
        } else {
            body.put("max_completion_tokens", maxCompletionTokens);
            if (reasoningEffort != null) {
                body.put("reasoning_effort", reasoningEffort);
            }
        }
        return body;
    }

    private String call(Map<String, Object> body) {
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