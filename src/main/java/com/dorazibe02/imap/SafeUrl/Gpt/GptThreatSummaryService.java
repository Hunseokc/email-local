package com.dorazibe02.imap.SafeUrl.Gpt;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GptThreatSummaryService {

    private final GptClient gptClient;

    public record AssessmentResult(int score, List<String> tags) {}

    public AssessmentResult assess(Map<String, Object> essentialInfo) {
        JsonNode stats = (JsonNode) essentialInfo.get("total_stats");
        Map<String, Long> detectionSummary = (Map<String, Long>) essentialInfo.get("detection_summary");

        int maliciousCount = stats.path("malicious").asInt(0);
        int suspiciousCount = stats.path("suspicious").asInt(0);

        // 점수 계산
        int score = 0;
        score += maliciousCount * 5;  // malicious 높은 가중치
        score += suspiciousCount * 2; // suspicious 낮은 가중치
        score = Math.min(score, 100); // 최대치 100

        // 태그 분류
        List<String> tags = new ArrayList<>();
        tags.add("Threat-Detected");

        // 가장 많이 탐지된 위협 유형이 대표 태그
        String topThreat = detectionSummary.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("generic");

        tags.add(topThreat.toUpperCase());

        return new AssessmentResult(score, tags);
    }

    public String summarizeVirusTotalReport(Map<String, Object> essentialInfo) {
        // 프롬프트 영어 사용 시 토큰 사용량 저렴
        String prompt = String.format(
                """
                You are a senior cybersecurity analyst AI. 
                Your task is to assess the threat level of a URL based on a summary from VirusTotal.
                Analyze the following data. Pay close attention to the reputation 
                and reliability of the specific security vendors that flagged 
                the URL as malicious or suspicious. 
                For example, detections from well-known vendors like Google, 
                Kaspersky, or BitDefender should be weighted more heavily than detections from obscure vendors.
    
                Based on your analysis, provide a response STRICTLY in the following JSON format:
                {
                    "Tag": "PHISHING",
                    "ThreatScore": 95,
                    "Content": "This URL was detected as phishing by several reputable engines, 
                    including Google SafeBrowse and BitDefender. Access is highly discouraged."
                }
    
                Here is the data to analyze:
                - Analyzed URL: %s
                - Overall Stats: %s
                - Malicious vendors: %s
                - Suspicious vendors: %s
    
                Provide your assessment in Korean.
                """,
                essentialInfo.get("analyzed_url"),
                essentialInfo.get("total_stats"),
                essentialInfo.get("malicious_vendors"),
                essentialInfo.get("suspicious_vendors")
        );

        return gptClient.summarize(prompt);
    }
}
