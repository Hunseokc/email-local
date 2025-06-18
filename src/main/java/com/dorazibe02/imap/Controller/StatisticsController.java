package com.dorazibe02.imap.Controller;

import com.dorazibe02.imap.Config.RedisConfig;
import com.dorazibe02.imap.Member.MemberService;
import com.dorazibe02.imap.UnSafeEmail.LogService;
import com.dorazibe02.imap.UnSafeEmail.UnSafeEmail;
import com.dorazibe02.imap.UnSafeEmail.UnSafeEmailDto;
import com.dorazibe02.imap.User.CustomUserDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final RedisConfig redisConfig;
    private final MemberService memberService;
    private final LogService logService;

    @GetMapping("/api-usage")
    public ResponseEntity<?> getApiUsageStatistics() throws Exception {
        long userId = memberService.getUserIdByAuth();

        List<String> labels = new ArrayList<>();
        List<Integer> googleData = new ArrayList<>();
        List<Integer> vtAndGptData = new ArrayList<>();

        LocalDate today = LocalDate.now();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yy-MM-dd");
        DateTimeFormatter redisKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 최근 30일간의 데이터를 조회
        for (int i = 0; i < 30; i++) {
            LocalDate date = today.minusDays(i);
            labels.add(date.format(dateFormatter));

            String redisDateKey = date.format(redisKeyFormatter);
            String googleKey = String.format("api-usage:user:%d:google:%s", userId, redisDateKey);
            String vtKey = String.format("api-usage:user:%d:virustotal:%s", userId, redisDateKey);
            String gptKey = String.format("api-usage:user:%d:chatgpt:%s", userId, redisDateKey);

            List<String> counts = redisConfig.redisTemplate().opsForValue().multiGet(Arrays.asList(googleKey, vtKey, gptKey));

            int googleCount = (counts.get(0) == null) ? 0 : Integer.parseInt(counts.get(0));
            int vtCount = (counts.get(1) == null) ? 0 : Integer.parseInt(counts.get(1));
            int gptCount = (counts.get(2) == null) ? 0 : Integer.parseInt(counts.get(2));

            googleData.add(googleCount);
            vtAndGptData.add(vtCount + gptCount);
        }

        // Chart.js 주입하기 위한 형태로 변환
        Collections.reverse(labels);
        Collections.reverse(googleData);
        Collections.reverse(vtAndGptData);

        Map<String, Object> response = new HashMap<>();
        response.put("labels", labels);

        List<Map<String, Object>> datasets = new ArrayList<>();
        Map<String, Object> googleDataset = new HashMap<>();
        googleDataset.put("label", "Google Safe Browse");
        googleDataset.put("data", googleData);
        googleDataset.put("borderColor", "rgb(75, 192, 192)");
        googleDataset.put("tension", 0.1);

        Map<String, Object> vtGptDataset = new HashMap<>();
        vtGptDataset.put("label", "VirusTotal & ChatGPT");
        vtGptDataset.put("data", vtAndGptData);
        vtGptDataset.put("borderColor", "rgb(255, 99, 132)");
        vtGptDataset.put("tension", 0.1);

        datasets.add(googleDataset);
        datasets.add(vtGptDataset);

        response.put("datasets", datasets);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/threats")
    public ResponseEntity<List<UnSafeEmailDto>> getThreatLogs() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return (ResponseEntity<List<UnSafeEmailDto>>) ResponseEntity.status(HttpStatus.UNAUTHORIZED);
        }

        CustomUserDetail userDetails = (CustomUserDetail) authentication.getPrincipal();
        List<UnSafeEmailDto> logs = logService.getRecentThreats(userDetails.getAuthId());
        return ResponseEntity.ok(logs);
    }
}
