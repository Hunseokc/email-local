package com.dorazibe02.imap.Email;

import com.dorazibe02.imap.Auth.AuthRepository;
import com.dorazibe02.imap.Notion.NotionQueryService;
import com.dorazibe02.imap.Redis.RedisCacheService;
import com.dorazibe02.imap.UnSafeEmail.UnSafeEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class PollingScheduler {

    private final UnSafeEmailRepository unSafeEmailRepository;

    private final EmailService emailService;
    private final NotionQueryService notionQueryService;
    private final TaskScheduler taskScheduler;
    private final RedisCacheService redisCacheService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    // 각 유저 작업 10초 간격 실행
    private static final long DELAY_BETWEEN_USERS_MS = 10000;

    // 2분마다 전체 폴링
    @Scheduled(cron = "0 */1 * * * *")
    public void startPollingCycle() {
        log.info("Starting new user email polling cycle.");
        List<String> users = redisCacheService.getActiveUserIds();
        System.out.println("스케줄링 수행 : " + users);
        if (users.isEmpty()) {
            log.info("폴링 대상 사용자가 없습니다.");
            return;
        }
        // 셔플을 통해 매번 다른 순서로 유저 처리
        Collections.shuffle(users);

        // 첫 번째 유저부터 폴링 시작
        scheduleNextUserPoll(users, new AtomicInteger(0));
    }

    private void scheduleNextUserPoll(final List<String> users, final AtomicInteger currentIndex) {
        // 현재 인덱스가 리스트 범위를 벗어나면 사이클 종료 (또는 0으로 리셋하여 무한 반복)
        if (currentIndex.get() >= users.size()) {
            log.info("Finished polling cycle for {} users.", users.size());
            // 무한 반복을 원한다면 currentIndex.set(0);
            return;
        }

        Runnable task = () -> {
            String currentUser = users.get(currentIndex.get());
            log.info("Polling email for user ID: {}", currentUser);

            try {
                emailService.pollUserMail(Long.parseLong(currentUser));
            } catch (Exception e) {
                log.error("Failed to poll email for user ID: {}", currentUser, e);
            }

            // 현재 작업 끝나면 다음 유저의 작업 예약
            currentIndex.incrementAndGet();
            scheduleNextUserPoll(users, currentIndex);
        };

        // 스케줄러에 다음 작업 예약 (첫 작업은 즉시, 이후 작업은 딜레이 후 실행)
        Instant nextExecutionTime = Instant.now().plusMillis(DELAY_BETWEEN_USERS_MS); // 시작 시점 딜레이
        taskScheduler.schedule(task, nextExecutionTime);
        log.info("Scheduled next poll for user index {} at {}", currentIndex.get(), nextExecutionTime);
    }

    @Scheduled(cron = "0 0 0 * * *") // 매일 0시 0분에 실행
    public void cleanupOldThreatLogs() {
        log.info("오래된 위협 로그 삭제 작업");
        try {
            ZonedDateTime sevenDaysAgo = ZonedDateTime.now().minusDays(7);
            String cutoffDate = sevenDaysAgo.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            unSafeEmailRepository.deleteOlderThan(cutoffDate);
            log.info("7일 이상 경과된 위협 로그를 성공적으로 삭제했습니다.");
        } catch (Exception e) {
            log.error("오래된 위협 로그 삭제 중 오류가 발생했습니다.", e);
        }
    }

    @Scheduled(fixedRate = 3600000) // 1시간 간격으로 Notion 'Waiting' 이메일 재처리
    public void reprocessNotionWaitingEmailsForAllUsers() throws Exception {
        List<String> userIds = redisCacheService.getActiveUserIds();
        if (userIds.isEmpty()) {
            log.info("no user");
            return;
        }
        for (String userId : userIds) {
            boolean isNotionConnect = notionQueryService.isConnect(Long.parseLong(userId));
            if (isNotionConnect) {
                executorService.submit(() -> {
                    try {
                        emailService.reprocessWaitingNotionEmails(Long.parseLong(userId));
                    } catch (Exception e) {
                        log.error("Notion 'Waiting' reprocessing failed : {} - {}", userId, e.getMessage());
                    }
                });
            }
        }
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void reprocessWaitingTasks() {
        log.info("Starting daily reprocessing of waiting tasks...");

        while (true) {
            String waitingTaskJson = redisCacheService.dequeueWaitingTask();
            if (waitingTaskJson == null) {
                break;
            }

            // 대기 중이던 작업을 다시 원래의 분석 큐에 넣음
            redisCacheService.enqueueUrlAnalysis(waitingTaskJson); // enqueueUrlAnalysis를 오버로딩하여 JSON을 직접 받도록 수정
            log.info("Moved a task from waiting queue to main analysis queue.");
        }
        log.info("Daily reprocessing finished.");
    }
}