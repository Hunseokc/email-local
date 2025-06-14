package com.dorazibe02.imap.SafeUrl.VirusTotal;

import com.dorazibe02.imap.RateLimitException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// VirusTotal URL 상세 분석 서비스
@Service
@RequiredArgsConstructor
public class VirusTotalScanService {

    private final VirusTotalClient virusTotalClient;
    private static final int MAX_POLLING_ATTEMPTS = 10;
    private static final long POLLING_INTERVAL_MS = 5000;

    public JsonNode getReportForUrl(String url) {
        // 스캔 요청
        JsonNode scanResponse = virusTotalClient.requestUrlScan(url);
        if (scanResponse == null || !scanResponse.has("data") || !scanResponse.get("data").has("id")) {
            System.err.println("Failed to submit URL for scanning: " + url);
            return null;
        }

        String analysisId = scanResponse.get("data").get("id").asText();

        for (int i = 0; i < MAX_POLLING_ATTEMPTS; i++) {
            try {
                // VT API 리포트 반환 대기
                if (i > 0) {
                    Thread.sleep(POLLING_INTERVAL_MS);
                }

                System.out.println("[DEBUG] Polling analysis report... Attempt " + (i + 1));
                JsonNode report = virusTotalClient.getAnalysisReport(analysisId);

                if (report != null && report.has("data") && report.get("data").has("attributes")) {
                    String status = report.get("data").get("attributes").path("status").asText();
                    System.out.println("[DEBUG] Current analysis status: " + status);

                    // 분석 완료 -> 리포트 반환 -> 종료
                    if ("completed".equalsIgnoreCase(status)) {
                        return report;
                    }
                }
            } catch (InterruptedException e) {
                System.err.println("Polling was interrupted.");
                Thread.currentThread().interrupt();
                return null;
            }
        }

        throw new RateLimitException("VirusTotal analysis timed out after " + MAX_POLLING_ATTEMPTS + " retries.");
    }

}
