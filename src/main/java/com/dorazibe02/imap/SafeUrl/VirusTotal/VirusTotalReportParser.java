package com.dorazibe02.imap.SafeUrl.VirusTotal;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class VirusTotalReportParser {

    private static final Set<String> PRIORITY_VENDORS = Set.of(
            "Google SafeBrowse", "Kaspersky", "BitDefender", "ESET",
            "Fortinet", "Sophos", "G-Data", "Avira", "McAfee", "Symantec",
            "TrendMicro", "F-Secure", "Webroot", "Emsisoft", "CRDF", "CyRadar",
            "Lionic", "alphaMountain.ai", "PhishTank", "OpenPhish"
    );

    // 토큰 낭비 방지를 위해 엔진 이름 추출은 최대 7개로 제한
    private static final int VENDOR_LIST_LIMIT = 7;

    public Map<String, Object> parse(JsonNode fullReport, String originalUrl) {
        if (fullReport == null) return Collections.emptyMap();

        Map<String, Object> essentialInfo = new HashMap<>();
        essentialInfo.put("analyzed_url", originalUrl);

        JsonNode attributes = fullReport.path("data").path("attributes");
        if (attributes.isMissingNode()) return essentialInfo;

        // 1. Extract overall stats
        JsonNode stats = attributes.path("stats");
        essentialInfo.put("total_stats", stats);

        // 2. Extract and intelligently sample vendor lists
        extractAndSampleVendors(attributes.path("results"), stats, essentialInfo);

        return essentialInfo;
    }

    private void extractAndSampleVendors(JsonNode resultsNode, JsonNode statsNode, Map<String, Object> essentialInfo) {
        if (resultsNode.isMissingNode()) return;

        List<String> maliciousVendors = new ArrayList<>();
        List<String> suspiciousVendors = new ArrayList<>();

        resultsNode.fields().forEachRemaining(entry -> {
            JsonNode resultNode = entry.getValue();
            String category = resultNode.path("category").asText();
            String engineName = resultNode.path("engine_name").asText();

            if ("malicious".equals(category)) {
                maliciousVendors.add(engineName);
            } else if ("suspicious".equals(category)) {
                suspiciousVendors.add(engineName);
            }
        });

        int maliciousCount = statsNode.path("malicious").asInt(0);
        List<String> finalVendorList;

        if (maliciousCount >= VENDOR_LIST_LIMIT) {
            finalVendorList = maliciousVendors.stream()
                    .filter(PRIORITY_VENDORS::contains)
                    .limit(VENDOR_LIST_LIMIT)
                    .collect(Collectors.toList());

            if (finalVendorList.size() < VENDOR_LIST_LIMIT) {
                Set<String> prioritySet = new HashSet<>(finalVendorList);
                List<String> remainingVendors = maliciousVendors.stream()
                        .filter(v -> !prioritySet.contains(v))
                        .limit(VENDOR_LIST_LIMIT - finalVendorList.size())
                        .toList();
                finalVendorList.addAll(remainingVendors);
            }
        } else {
            finalVendorList = maliciousVendors.stream().limit(VENDOR_LIST_LIMIT).toList();
        }

        essentialInfo.put("malicious_vendors", finalVendorList);
        essentialInfo.put("suspicious_vendors", suspiciousVendors.stream().limit(3).toList());  // suspicious는 3개만
    }
}
