package com.dorazibe02.imap.Redis;

import com.dorazibe02.imap.SafeUrl.Dto.UrlAnalysisRequest;
import com.dorazibe02.imap.Config.RedisConfig;
import com.dorazibe02.imap.Setting.SettingFeature;
import com.dorazibe02.imap.Setting.ThreatAction;
import com.dorazibe02.imap.Setting.UserSetting;
import com.dorazibe02.imap.Setting.UserSettingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisCacheService {
    private final UserSettingRepository userSettingRepository;

    private final RedisConfig redisConfig;
    private final ObjectMapper objectMapper;

    private static final String ACTIVE_USERS_KEY = "active_user_ids";
    private static final String QUEUE_KEY = "url-analysis-queue";

    // ----------- last Uid 메서드 --------------
    private String key(String userId) {
        return "lastUid:" + userId;
    }

    public long getLastUid(String userId) {
        String val = redisConfig.redisTemplate().opsForValue().get(key(userId));
        return val != null ? Long.parseLong(val) : 0L;
    }

    public void setLastUid(String userId, long uid) {
        redisConfig.redisTemplate().opsForValue().set(key(userId), String.valueOf(uid));
    }

    public void resetLastUid(String userId) {
        redisConfig.redisTemplate().delete(key(userId));
        log.info("Redis lastUid for user {} has been reset.", userId);
    }
    // --------------------------------


    // ----------- Active User 메서드 ----------------
    // 현재 저장된 active_user_ids 리스트를 조회
    public List<String> getActiveUserIds() {
        List<String> list = redisConfig.redisTemplate().opsForList().range(ACTIVE_USERS_KEY, 0, -1);
        log.info("현재 등록된 풀링 유저 번호 : {}", list);
        return list != null ? list : new ArrayList<>();
    }

    // 특정 사용자 ID가 목록에 등록되어 있는지 확인
    public boolean isUserActive(String userId) {
        return getActiveUserIds().contains(userId);
    }

    // 스케줄러 user 추가
    public boolean activateUser(String userId) {
        if (!isUserActive(userId)) {
            // 리스트의 왼쪽에 추가 (순서 상 상관없이)
            redisConfig.redisTemplate().opsForList().leftPush(ACTIVE_USERS_KEY, userId);
            return true;
        }
        return false;
    }

    // 스케줄러 user 삭제
    public boolean deactivateUser(String userId) {
        if (isUserActive(userId)) {
            redisConfig.redisTemplate().opsForList().remove(ACTIVE_USERS_KEY, 0, userId);
            return true;
        } else {
            return false;
        }
    }
    // --------------------------------

    // ----------- 큐 처리 ----------------
    public void enqueueUrlAnalysis(UrlAnalysisRequest request) {
        if (request.getUrls() == null || request.getUrls().isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(request);
            redisConfig.redisTemplate().opsForList().rightPush(QUEUE_KEY, json);
        } catch (JsonProcessingException e) {
            log.error("URL 분석 요청 실패: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public void enqueueUrlAnalysis(String requestOfJson) {
        redisConfig.redisTemplate().opsForList().rightPush(QUEUE_KEY, requestOfJson);
    }

    private static final String WAITING_QUEUE_KEY = "url_analysis_waiting_queue";

    public void enqueueWaitingTask(UrlAnalysisRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            redisConfig.redisTemplate().opsForList().rightPush(WAITING_QUEUE_KEY, json);
        } catch (JsonProcessingException e) {
            log.error("Waiting Queue 분석 요청 실패: {}", e.getMessage());
        }
    }

    public String dequeueWaitingTask() {
        return redisConfig.redisTemplate().opsForList().leftPop(WAITING_QUEUE_KEY);
    }
    // --------------------------------

    // GPT 캐싱
    public String getGptCache(String key) {
        return redisConfig.redisTemplate().opsForValue().get(key);
    }

    public void setGptCache(String key, String response, long timeoutInSeconds) {
        redisConfig.redisTemplate().opsForValue().set(key, response, timeoutInSeconds, TimeUnit.SECONDS);
    }
    // --------------------------------

    // ------------------ ENUM 활용한 범용 기능 -------------------------------
    // Cache-Aside
    // Redis 키 생성 메서드
    private String getSettingKey(long userId, String featureKey) {
        return "user:" + userId + ":settings:" + featureKey;
    }

    // 설정 조회 메서드
    public boolean getSettingValue(long userId, SettingFeature feature) {
        String redisKey = getSettingKey(userId, String.valueOf(feature));
        String cachedValue = redisConfig.redisTemplate().opsForValue().get(redisKey);

        if (cachedValue != null) {
            return Boolean.parseBoolean(cachedValue);
        }

        UserSetting setting = userSettingRepository.findByAuthId(userId)
                .orElseThrow(() -> new RuntimeException("UserSetting not found for authId: " + userId + ". It should have been created during registration."));

        boolean isEnabled = feature.getGetter().apply(setting);

        redisConfig.redisTemplate().opsForValue().set(redisKey, String.valueOf(isEnabled));
        return isEnabled;
    }

    // 설정 변경
    @Transactional
    public void setSettingValue(long userId, SettingFeature feature, boolean isEnabled) {
        UserSetting setting = userSettingRepository.findByAuthId(userId)
                .orElseThrow(() -> new RuntimeException("UserSetting not found for authId: " + userId));
        feature.getSetter().accept(setting, isEnabled);
        userSettingRepository.save(setting);

        String redisKey = getSettingKey(userId, String.valueOf(feature));
        redisConfig.redisTemplate().opsForValue().set(redisKey, String.valueOf(isEnabled));
    }

    public ThreatAction getThreatAction(long userId) {
        String redisKey = getSettingKey(userId, "threatAction");
        String cachedValue = redisConfig.redisTemplate().opsForValue().get(redisKey);

        if (cachedValue != null) {
            return ThreatAction.valueOf(cachedValue);
        }

        UserSetting setting = userSettingRepository.findByAuthId(userId)
                .orElseThrow(() -> new RuntimeException("UserSetting not found for authId: " + userId));

        ThreatAction actionFromDb = setting.getThreatAction();

        redisConfig.redisTemplate().opsForValue().set(redisKey, actionFromDb.name());

        return actionFromDb;
    }

    @Transactional
    public void setThreatAction(long userId, ThreatAction action) {
        UserSetting setting = userSettingRepository.findByAuthId(userId)
                .orElseThrow(() -> new RuntimeException("UserSetting not found for authId: " + userId));
        setting.setThreatAction(action);
        userSettingRepository.save(setting);

        String redisKey = getSettingKey(userId, "threatAction");
        redisConfig.redisTemplate().opsForValue().set(redisKey, action.name());
    }
}
