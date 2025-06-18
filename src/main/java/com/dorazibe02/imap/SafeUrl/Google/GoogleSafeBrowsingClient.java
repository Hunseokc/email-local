package com.dorazibe02.imap.SafeUrl.Google;

import com.dorazibe02.imap.Vault.AuthCustomRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@Service
public class GoogleSafeBrowsingClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleSafeBrowsingClient.class);

    @Value("${api-keys.google}")
    private String apiKey;

    private WebClient webClient;
    private final WebClient.Builder webClientBuilder;

    public GoogleSafeBrowsingClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, AuthCustomRepository authCustomRepository) {
        this.webClientBuilder = webClientBuilder;
    }

    @PostConstruct
    public void init() {
        try {
            if (this.apiKey == null) {
                throw new IllegalStateException("GOOGLE_API_KEY not found in properties.");
            }
            this.webClient = webClientBuilder
                    .baseUrl("https://safebrowsing.googleapis.com")
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
            log.info("Google Safe Browse API Key and WebClient loaded from Supabase Vault successfully.");
        } catch (Exception e) {
            log.error("Failed to load Google Safe Browse API Key from Supabase Vault: {}", e.getMessage(), e);
            throw new RuntimeException("Google Safe Browse API Key loading failed.", e);
        }
    }

    // isUnsafe 메서드 (boolean 반환)
    public boolean isUnsafe(String url) {
        if (apiKey == null) { // API 키 로드 실패 시
            log.warn("Google Safe Browse API Key is not available. Skipping isUnsafe check for URL: {}", url);
            return false;
        }

        Map<String, Object> requestBody = Map.of(
                "client", Map.of("clientId", "your-app", "clientVersion", "1.0"),
                "threatInfo", Map.of(
                        "threatTypes", List.of("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"),
                        "platformTypes", List.of("ANY_PLATFORM"),
                        "threatEntryTypes", List.of("URL"),
                        "threatEntries", List.of(Map.of("url", url))
                )
        );

        try {
            log.debug("Requesting Google Safe Browse for single URL: {}", url);
            JsonNode root = webClient.post()
                    .uri("/v4/threatMatches:find?key=" + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            boolean unsafe = root != null && root.has("matches");
            if (unsafe) {
                log.warn("URL marked as unsafe: {}", url);
            }
            return unsafe;
        } catch (Exception e) {
            log.error("Error in isUnsafe while checking URL {}: {}", url, e.getMessage(), e);
            return false;
        }
    }

    public JsonNode findThreatMatches(List<String> urls) {
        if (apiKey == null || urls == null || urls.isEmpty()) {
            return new ObjectMapper().createObjectNode();
        }

        List<Map<String, String>> threatEntries = urls.stream()
                .map(u -> Map.of("url", u))
                .toList();

        Map<String, Object> requestBody = Map.of(
                "client", Map.of("clientId", "your-app-name", "clientVersion", "1.0"),
                "threatInfo", Map.of(
                        "threatTypes", List.of("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"),
                        "platformTypes", List.of("ANY_PLATFORM"),
                        "threatEntryTypes", List.of("URL"),
                        "threatEntries", threatEntries
                )
        );

        try {
            log.debug("Requesting Google Safe Browse for URLs: {}", urls);
            return webClient.post()
                    .uri("/v4/threatMatches:find?key=" + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (Exception e) {
            log.error("Error in findThreatMatches for URLs {}: {}", urls, e.getMessage(), e);
            return new ObjectMapper().createObjectNode();
        }
    }
}