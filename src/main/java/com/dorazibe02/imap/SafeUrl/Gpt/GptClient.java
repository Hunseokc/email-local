package com.dorazibe02.imap.SafeUrl.Gpt;

import com.dorazibe02.imap.RateLimitException;
import com.dorazibe02.imap.Redis.RedisCacheService;
import com.dorazibe02.imap.Vault.AuthCustomRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GptClient {

    private final RedisCacheService redisCacheService;

    @Value("${api-keys.openai}")
    private String apiKey;

    private WebClient webClient;
    private final WebClient.Builder webClientBuilder;

    public GptClient(WebClient.Builder webClientBuilder, RedisCacheService redisCacheService) {
        this.webClientBuilder = webClientBuilder;
        this.redisCacheService = redisCacheService;
    }

    @PostConstruct
    public void init() {
        try {
            if (this.apiKey == null) {
                throw new IllegalStateException("OPENAI_API_KEY not found in properties.");
            }
            this.webClient = webClientBuilder
                    .baseUrl("https://api.openai.com/v1/chat/completions")
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
            log.info("OpenAI API Key and WebClient loaded from properties successfully.");
        } catch (Exception e) {
            log.error("Failed to load OpenAI API Key from Supabase Vault: {}", e.getMessage());
            throw new RuntimeException("OpenAI API Key loading failed.", e);
        }
    }

    public String summarize(String prompt) {
        // 해시 탐색
        String cacheKey = "openai_cache:" + DigestUtils.sha256Hex(prompt);

        // 캐시 조회
        String cachedResponse = redisCacheService.getGptCache(cacheKey);
        if (cachedResponse != null) {
            log.info("GPT Response Cache HIT! Returning cached data for key: {}", cacheKey);
            return cachedResponse;
        }

        // 캐시 없으면 API 호출
        log.info("GPT Response Cache MISS. Calling OpenAI API...");
        String apiResponse = callGptApiWithRetry(prompt);

        // 성공 응답 캐시 저장 (24시간)
        if (apiResponse != null && !apiResponse.contains("실패")) {
            log.info("Caching new GPT response for key: {}", cacheKey);
            redisCacheService.setGptCache(cacheKey, apiResponse, 24 * 60 * 60); // 24시간
        }

        return apiResponse;
    }

    private String callGptApiWithRetry(String prompt) {
        int maxRetries = 3; // 최대 재시도 횟수 (429 발생하면 재시도)
        long delay = 1000; // 초기 대기 시간 (1초)
        for (int i = 0; i < maxRetries; i++) {
            try {
                Map<String, Object> message = Map.of(
                        "role", "user",
                        "content", prompt
                );

                Map<String, Object> requestBody = Map.of(
                        "model", "gpt-3.5-turbo",
                        "messages", List.of(message),
                        "temperature", 0.7
                );

                JsonNode response = webClient.post()
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block();

                return response
                        .path("choices")
                        .get(0)
                        .path("message")
                        .path("content")
                        .asText();

            } catch (WebClientResponseException e) {
                if (e.getStatusCode().value() == 429) {
                    System.err.println("OpenAI API rate limit exceeded. Retrying after " + delay + "ms... (Attempt " + (i + 1) + ")");
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    delay *= 2; // 대기 시간을 2배로 늘림
                } else {
                    // 429가 아닌 다른 오류는 즉시 예외를 던짐
                    e.printStackTrace();
                    return "GPT 요약 실패: " + e.getMessage();
                }
            } catch (Exception e) {
                e.printStackTrace();
                return "GPT 요약 실패: " + e.getMessage();
            }
        }

        // 최대 재시도 횟수를 초과한 경우
        throw new RateLimitException("OpenAI API request failed after " + maxRetries + " retries.");
    }
}
