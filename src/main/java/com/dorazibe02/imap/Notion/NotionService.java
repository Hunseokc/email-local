package com.dorazibe02.imap.Notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotionService {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private final NotionEntityService notionEntityService;
    private final NotionQueryService notionQueryService;

    public String saveEmailToNotion(Long userId, EmailMessage email) throws Exception {
        Notion notionInfo;
        try {
            notionInfo = notionQueryService.getNotionInfo(userId);
        } catch (RuntimeException e) {
            System.err.println("Notion 연동 정보가 없어 이메일을 Notion에 저장할 수 없습니다: " + e.getMessage());
            throw new RuntimeException("Notion 연동이 필요합니다.");
        }

        return createNotionPage(notionEntityService.getDecryptedApiSecret(userId), notionInfo.getNotionDbId(), email);
    }

    public void testCreatePage(String apiKey, String dbId) throws Exception {
        // 테스트용 이메일 메시지 객체 생성
        EmailMessage testEmail = new EmailMessage(
                "[테스트] Notion 연동 성공",
                "Email-Plugin-System",
                ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                List.of("Test"),
                "이 메시지가 보인다면, Notion API 연동이 성공적으로 완료된 것입니다.",
                "Read"
        );
        createNotionPage(apiKey, dbId, testEmail);
    }

    private String createNotionPage(String apiKey, String dbId, EmailMessage email) throws Exception {
        Map<String, Object> properties = Map.of(
                "Subject", Map.of(
                        "title", List.of(
                                Map.of("text", Map.of("content", email.getSubject()))
                        )
                ),
                "From", Map.of(
                        "email", email.getFrom()
                ),
                "Date", Map.of(
                        "date", Map.of("start", email.getDate())
                ),
                "Tags", Map.of(
                        "multi_select", List.of(
                                Map.of("name", "Waiting...")
                        )
                ),
                "Content", Map.of(
                        "rich_text", List.of(
                                Map.of("text", Map.of("content", email.getContent()))
                        )
                ),
                "Status", Map.of(
                        "status", Map.of("name", email.getStatus())
                )
        );

        Map<String, Object> body = Map.of(
                "parent", Map.of("database_id", dbId),
                "properties", properties
        );

        String responseBody = webClient.post()
                .uri("https://api.notion.com/v1/pages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header("Notion-Version", "2022-06-28")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .map(errorBody -> {
                                    System.err.println("Notion API Error Response (POST): " + errorBody);
                                    return new WebClientResponseException(
                                            clientResponse.statusCode().toString(),
                                            clientResponse.statusCode().value(),
                                            clientResponse.statusCode().toString(),
                                            null,
                                            errorBody.getBytes(),
                                            null
                                    );
                                })
                )
                .bodyToMono(String.class)
                .block();

        if (responseBody != null) {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.get("id").asText();
        } else {
            throw new RuntimeException("Notion 저장 실패: 응답 본문 없음");
        }
    }

    public void updateNotionTags(Long userId, String notionPageId, List<String> tagNames, int threatScore, String summary) throws Exception {
        Notion notionInfo;
        try {
            notionInfo = notionQueryService.getNotionInfo(userId);
        } catch (RuntimeException e) {
            System.err.println("Notion 연동 정보가 없어 Notion 태그를 업데이트할 수 없습니다: " + e.getMessage());
            return;
        }

        List<Map<String, Object>> notionTags = tagNames.stream()
                .map(tag -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", tag);
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> properties = Map.of(
                "Tags", Map.of("multi_select", notionTags),
                "ThreatScore", Map.of("number", threatScore),
                "GPT_Summary", Map.of(
                        "rich_text", List.of(Map.of(
                                "type", "text",
                                "text", Map.of("content", summary)
                        ))
                )
        );
        Map<String, Object> body = Map.of("properties", properties);

        try {
            webClient.patch()
                    .uri("https://api.notion.com/v1/pages/" + notionPageId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionEntityService.getDecryptedApiSecret(userId))
                    .header("Notion-Version", "2022-06-28")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .map(errorBody -> {
                                        System.err.println("Notion API Error Response (PATCH): " + errorBody);
                                        return new WebClientResponseException(
                                                clientResponse.statusCode().toString(),
                                                clientResponse.statusCode().value(),
                                                clientResponse.statusCode().toString(),
                                                null,
                                                errorBody.getBytes(),
                                                null
                                        );
                                    })
                    )
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            System.err.println("Notion 페이지 업데이트 실패: " + e.getMessage());
            if (e instanceof WebClientResponseException) {
                WebClientResponseException webClientEx = (WebClientResponseException) e;
                System.err.println("WebClient Response Status: " + webClientEx.getStatusCode());
                System.err.println("WebClient Response Body (re-logged): " + webClientEx.getResponseBodyAsString());
            }
            throw e;
        }
    }

    public List<Map<String, String>> getWaitingEmailsFromNotion(Long userId) throws Exception {
        Notion notionInfo;
        try {
            notionInfo = notionQueryService.getNotionInfo(userId);
        } catch (RuntimeException e) {
            System.err.println("Notion 연동 정보가 없어 'Waiting' 이메일을 조회할 수 없습니다: " + e.getMessage());
            return new ArrayList<>();
        }

        Map<String, Object> filter = Map.of(
                "property", "Tags",
                "multi_select", Map.of(
                        "contains", "Waiting..."
                )
        );

        Map<String, Object> body = Map.of("filter", filter);

        String responseBody = webClient.post()
                .uri("https://api.notion.com/v1/databases/" + notionInfo.getNotionDbId() + "/query")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionEntityService.getDecryptedApiSecret(userId))
                .header("Notion-Version", "2022-06-28")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .map(errorBody -> {
                                    System.err.println("Notion API Error Response (QUERY): " + errorBody);
                                    return new RuntimeException("Notion API returned " + clientResponse.statusCode() + ": " + errorBody);
                                })
                )
                .bodyToMono(String.class)
                .block();


        List<Map<String, String>> waitingEmails = new ArrayList<>();
        if (responseBody != null) {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode results = root.get("results");

            if (results.isArray()) {
                for (JsonNode page : results) {
                    String pageId = page.get("id").asText();
                    JsonNode properties = page.get("properties");

                    String subject = properties.path("Subject").path("title").get(0).path("text").path("content").asText("No Subject");
                    String from = properties.path("From").path("email").asText("No Sender");
                    String date = properties.path("Date").path("date").path("start").asText("No Date");
                    String content = properties.path("Content").path("rich_text").get(0).path("text").path("content").asText("");

                    Map<String, String> emailInfo = new HashMap<>();
                    emailInfo.put("pageId", pageId);
                    emailInfo.put("subject", subject);
                    emailInfo.put("from", from);
                    emailInfo.put("date", date);
                    emailInfo.put("content", content);
                    waitingEmails.add(emailInfo);
                }
            }
        } else {
            throw new RuntimeException("Notion에서 'Waiting' 이메일 조회 실패: 응답 본문 없음");
        }
        return waitingEmails;
    }
}