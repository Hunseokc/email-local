package com.dorazibe02.imap.SafeUrl;

import com.dorazibe02.imap.Auth.Auth;
import com.dorazibe02.imap.Auth.AuthRepository;
import com.dorazibe02.imap.Config.RedisConfig;
import com.dorazibe02.imap.Email.MailActionService;
import com.dorazibe02.imap.Notion.NotionQueryService;
import com.dorazibe02.imap.Notion.NotionService;
import com.dorazibe02.imap.RateLimitException;
import com.dorazibe02.imap.Redis.RedisCacheService;
import com.dorazibe02.imap.SafeUrl.Dto.UrlAnalysisRequest;
import com.dorazibe02.imap.SafeUrl.Google.GoogleSafeBrowsingService;
import com.dorazibe02.imap.SafeUrl.Gpt.GptThreatSummaryService;
import com.dorazibe02.imap.SafeUrl.Heuristic.HeuristicAnalysisService;
import com.dorazibe02.imap.SafeUrl.VirusTotal.VirusTotalReportParser;
import com.dorazibe02.imap.SafeUrl.VirusTotal.VirusTotalScanService;
import com.dorazibe02.imap.Setting.SettingFeature;
import com.dorazibe02.imap.UnSafeEmail.UnSafeEmail;
import com.dorazibe02.imap.UnSafeEmail.UnSafeEmailRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.text.SimpleDateFormat;

import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class UrlAnalysisConsumer {

    private final GoogleSafeBrowsingService safeBrowseService;
    private final VirusTotalScanService virusTotalScanService;
    private final GptThreatSummaryService gptService;
    private final NotionQueryService notionQueryService;
    private final NotionService notionService;
    private final MailActionService mailActionService;
    private final VirusTotalReportParser reportParser;
    private final HeuristicAnalysisService heuristicService;
    private final RedisCacheService redisCacheService;

    private final AuthRepository authRepository;
    private final UnSafeEmailRepository unSafeEmailRepository;

    private final RedisConfig redisConfig;

    private void incrementApiUsage(long userId, String apiName, int count) {
        try {
            String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            String redisKey = String.format("api-usage:user:%d:%s:%s", userId, apiName, date);
            redisConfig.redisTemplate().opsForValue().increment(redisKey, count);
        } catch (Exception e) {
            log.error("Failed to count API usage %s: %s\n", apiName, e.getMessage());
        }
    }

    @Async("taskExecutor")
    public void consumeAsync(UrlAnalysisRequest request) throws Exception {
        long userId = request.getUserId();

        boolean notionSaveEnable = notionQueryService.isConnect(userId);

        List<String> allUrls = request.getUrls();
        if (allUrls == null || allUrls.isEmpty()) return;

        String pageId = "";
        if (notionSaveEnable) pageId = request.getPageId();

        try {
            String step = request.getStartFromStep(); // 검사 진행 상태

            // 상세 검사 대상 Set 만들기
            Set<String> urlsForDetailedScan;

            if ("VT_ANALYSIS".equals(step)) {
                // VT 단계부터 재시작하는 경우, 모든 URL을 상세 검사 대상으로 간주
                log.info("VirusTotal 분석부터 재시작 : 유저 {}.", userId);
                urlsForDetailedScan = new HashSet<>(request.getUrls());
            } else {
                // 현재 검사 설정 확인
                boolean useDetailedScan = redisCacheService.getSettingValue(userId, SettingFeature.ALWAYS_DETAILED_SCAN);

                if (useDetailedScan) {
                    // 강력한 검증이면 모든 URL VT & gpt 검사
                    urlsForDetailedScan = new HashSet<>(request.getUrls());
                } else {
                    // 기본 검증은 Google & Heuristic -> 안전하지 않은 경우만 상세 분석
                    // Google Safe Browsing URL 검사 (옵션 기준 일반 검사)
                    List<String> unsafeUrlsByGoogle = safeBrowseService.findUnsafeUrls(allUrls);
                    System.out.println(unsafeUrlsByGoogle);
                    incrementApiUsage(userId, "google", 1);

                    // 구글 API 동작 결과 위협 URL -> 상세 검사 대상
                    urlsForDetailedScan = new HashSet<>(unsafeUrlsByGoogle);

                    // 안전 URL List 생성
                    List<String> safeUrlsByGoogle = allUrls.stream()
                            .filter(url -> !urlsForDetailedScan.contains(url))
                            .toList();

                    // 해당 리스트(통과 URL) 휴리스틱 검사
                    if (!safeUrlsByGoogle.isEmpty()) {
                        boolean isSuspicious = heuristicService.isSuspicious(
                                request.getFrom(),
                                safeUrlsByGoogle
                        );

                        if (isSuspicious) {
                            // 휴리스틱에 탐지되면 통과 URL 이지만 상세 검사 대상으로 추가
                            urlsForDetailedScan.addAll(safeUrlsByGoogle);
                        }
                    }

                    // 다 통과했으면 "Safe"로 처리하고 종료
                    if (urlsForDetailedScan.isEmpty()) {
                        log.info("안전한 URL");
                        if (pageId != null && notionSaveEnable) {
                            notionService.updateNotionTags(
                                    userId, pageId, List.of("Safe"), 0, "모든 URL이 Google Safe Browse 기준 안전합니다."
                            );
                        }
                        return;
                    }
                }
            }

            if (urlsForDetailedScan.isEmpty()) return;

            // VirusTotal, GPT 상세 분석 진행 (옵션 기준 상세, 강력 검사)
            log.info("위험 URL -> Virus Total API 수행");
            for (String finalUrlToScan : urlsForDetailedScan) {
                // VirusTotal 검사
                log.info("VirusTotal API URL : {}", finalUrlToScan);
                JsonNode fullReport = virusTotalScanService.getReportForUrl(finalUrlToScan);
                incrementApiUsage(userId, "virustotal", 1);

                if (fullReport == null) continue;

                // 리포트 내용 파싱
                Map<String, Object> essentialInfo = reportParser.parse(fullReport, finalUrlToScan);

                // GPT 요약
                String jsonSummary = gptService.summarizeVirusTotalReport(essentialInfo);
                incrementApiUsage(userId, "chatgpt", 1);

                // 결과 종합
                JsonNode node = new ObjectMapper().readTree(jsonSummary);
                List<String> tags = List.of(node.path("Tag").asText("Threat-Detected"));
                int score = node.path("ThreatScore").asInt(50);
                String content = node.path("Content").asText("분석 중 오류가 발생했습니다.");

                if (score >= 60) {
                    UnSafeEmail log = new UnSafeEmail();
                    log.setSubject(request.getSubject());
                    log.setFromAddress(request.getFrom());
                    log.setReceivedDate(request.getDate());
                    log.setTag(tags.getFirst());
                    log.setThreatScore(score);

                    Auth auth = authRepository.getReferenceById(request.getUserId());
                    log.setAuth(auth);

                    unSafeEmailRepository.save(log);
                }

                // 최종 결과 Notion 업데이트
                if (pageId != null && notionSaveEnable) {
                    notionService.updateNotionTags(
                            userId, pageId, tags, score, content
                    );
                }

                // 위협 메일 처리
                mailActionService.processMailAction(userId, Long.parseLong(request.getMailUid()), score, tags);
            }
        } catch (RateLimitException e) {
            // API 한도 초과 예외 처리
            log.warn("API 한도 초과 : user {}. 원인 : {}", userId, e.getMessage());

            if (e.getMessage().contains("VirusTotal")) {
                request.setStartFromStep("VT_ANALYSIS");
            }

            // 사용자의 '강력한 검증' 설정을 '기본 검증'으로 (일단 동작하는게 우선)
            redisCacheService.setSettingValue(userId, SettingFeature.ALWAYS_DETAILED_SCAN, false);

            // 실패한 요청 DTO "대기 큐"에 저장
            redisCacheService.enqueueWaitingTask(request);
        } catch (Exception e) {
            log.error("URL 일괄 분석 실패 (User ID: {}): {}", userId, e.getMessage());
        }
    }
}