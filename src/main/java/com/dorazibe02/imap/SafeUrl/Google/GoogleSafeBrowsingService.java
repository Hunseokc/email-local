package com.dorazibe02.imap.SafeUrl.Google;

import com.dorazibe02.imap.SafeUrl.Dto.UrlAnalysisResult;
import com.dorazibe02.imap.SafeUrl.Dto.SingleUrlAnalysisResult;
import com.dorazibe02.imap.SafeUrl.UrlScanService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GoogleSafeBrowsingService implements UrlScanService {

    private final GoogleSafeBrowsingClient safeBrowsingClient;

    @Override
    public UrlAnalysisResult analyzeUrls(List<String> urls) {
        Map<String, SingleUrlAnalysisResult> results = new HashMap<>();

        for (String url : urls) {
            boolean isUnsafe = safeBrowsingClient.isUnsafe(url);

            SingleUrlAnalysisResult result = new SingleUrlAnalysisResult(
                    url,
                    null, // GPT summary 나중에
                    !isUnsafe,
                    List.of(isUnsafe ? "Malicious" : "Safety"), // tags
                    isUnsafe ? 80 : 0,
                    ""
            );

            results.put(url, result);
        }

        return new UrlAnalysisResult(results);
    }

    public SingleUrlAnalysisResult analyzeUrl(String url) {
        boolean isUnsafe = safeBrowsingClient.isUnsafe(url);

        SingleUrlAnalysisResult result = new SingleUrlAnalysisResult(
                url,
                null,
                !isUnsafe,
                List.of(isUnsafe ? "Malicious" : "Safety"), // tags
                isUnsafe ? 80 : 0,
                ""
        );
        return result;
    }

    public List<String> findUnsafeUrls(List<String> urls) {
        JsonNode responseNode = safeBrowsingClient.findThreatMatches(urls);
        List<String> unsafeUrls = new ArrayList<>();

        if (responseNode != null && responseNode.has("matches")) {
            for (JsonNode match : responseNode.get("matches")) {
                if (match.has("threat") && match.get("threat").has("url")) {
                    unsafeUrls.add(match.get("threat").get("url").asText());
                }
            }
        }
        return unsafeUrls;
    }
}