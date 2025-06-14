package com.dorazibe02.imap.SafeUrl;

import com.dorazibe02.imap.SafeUrl.Dto.SingleUrlAnalysisResult;
import com.dorazibe02.imap.SafeUrl.Dto.UrlAnalysisResult;
import com.dorazibe02.imap.SafeUrl.Gpt.GptThreatSummaryService;
import com.dorazibe02.imap.Notion.NotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ThreatIntelligenceService {

    @Qualifier("googleSafeBrowsingService")
    private final UrlScanService googleSafeBrowsingService;

    @Qualifier("virusTotalScanService")
    private final UrlScanService virusTotalScanService;

    private final GptThreatSummaryService gptThreatSummaryService;
    private final NotionService notionService;

    @Async
    public void analyzeAndUpdate(long userId, String notionPageId, List<String> urls) throws Exception {
        UrlAnalysisResult safeBrowsingResult = googleSafeBrowsingService.analyzeUrls(urls);

        // 위험한 URL만 선별
        List<String> dangerousUrls = safeBrowsingResult.getResults().values().stream()
                .filter(r -> !r.isSafe())
                .map(SingleUrlAnalysisResult::getUrl)
                .toList();
    }
}