package com.dorazibe02.imap.Redis;

import com.dorazibe02.imap.Config.RedisConfig;
import com.dorazibe02.imap.SafeUrl.Dto.UrlAnalysisRequest;
import com.dorazibe02.imap.SafeUrl.UrlAnalysisConsumer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisQueueListener {

    private final RedisConfig redisConfig;
    private final ObjectMapper objectMapper;
    private final UrlAnalysisConsumer consumer;

    private static final String QUEUE_KEY = "url-analysis-queue";

    @Scheduled(fixedDelay = 3000) // 3초 간격으로 큐 polling
    public void listen() {
        String json = redisConfig.redisTemplate().opsForList().leftPop(QUEUE_KEY);
        if (json != null) {
            try {
                UrlAnalysisRequest request = objectMapper.readValue(json, UrlAnalysisRequest.class);
                consumer.consumeAsync(request); // 비동기로 처리
            } catch (JsonProcessingException e) {
                System.err.println("URL 분석 요청 디코딩 실패: " + e.getMessage());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
