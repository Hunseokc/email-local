package com.dorazibe02.imap.Controller;

import com.dorazibe02.imap.Redis.RedisCacheService;
import com.dorazibe02.imap.Email.EmailService;
import com.dorazibe02.imap.Member.MemberService;
import com.dorazibe02.imap.Setting.SettingFeature;
import com.dorazibe02.imap.User.CustomUserDetail;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/email")
public class EmailRestController {
    private final EmailService emailService;
    private final MemberService memberService;
    private final RedisCacheService redisCacheService;

    @GetMapping("/active-users")
    public ResponseEntity<?> activeUsers() throws Exception {
        List<String> users = redisCacheService.getActiveUserIds();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/activate-email")
    public ResponseEntity<String> active() throws Exception {
        // 현재 로그인 유저 정보 -> CustomUserDetail 캐스팅해서 email GET
        String userId = memberService.getUserIdStrByAuth();

        if (redisCacheService.activateUser(userId)){
            String message = redisCacheService.isUserActive(String.valueOf(userId))+"메일 폴링 스케줄러에 등록되었습니다.";
            return ResponseEntity.ok(message);
        } else{
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("유저 등록 오류 발생");
        }
    }

    @PostMapping("/deactivate-email")
    public ResponseEntity<String> deactive() throws Exception {
        // 현재 로그인 유저 정보 -> CustomUserDetail로 캐스팅해서 email GET
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증 필요");
        }

        CustomUserDetail userDetails = (CustomUserDetail) authentication.getPrincipal();
        long userId = memberService.getUserIdByEmail(userDetails.getUsername());

        if (redisCacheService.deactivateUser(String.valueOf(userId))) {
            String message = redisCacheService.isUserActive(String.valueOf(userId))+"메일 폴링 스케줄러에서 해제되었습니다.";
            return ResponseEntity.ok(message);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("유저 등록 해제 오류 발생");
        }
    }

    // URL 검증 오류 시 다시 처리
    @PostMapping("/reprocess-waiting")
    public ResponseEntity<String> reprocessWaitingEmails() throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증 필요");
        }

        CustomUserDetail userDetails = (CustomUserDetail) authentication.getPrincipal();
        long userId = memberService.getUserIdByEmail(userDetails.getUsername());

        emailService.reprocessWaitingNotionEmails(userId);
        return ResponseEntity.ok("Notion 'Waiting' 이메일 재처리 요청이 시작되었습니다.");
    }

    @PostMapping("/reset-uid")
    public ResponseEntity<String> resetUid() {
        try {
            String userIdStr = memberService.getUserIdStrByAuth();
            redisCacheService.resetLastUid(userIdStr);
            return ResponseEntity.ok("메일 UID가 성공적으로 초기화되었습니다. 다음 메일 가져오기 시 모든 메일을 다시 확인합니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("UID 초기화 실패: " + e.getMessage());
        }
    }

    @PostMapping("/poll-now")
    public ResponseEntity<String> pollMailNow() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증 필요");
            }
            long userId = memberService.getUserIdByAuth();

            emailService.pollUserMail(userId);
            return ResponseEntity.ok("새로운 메일 확인 요청이 완료되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("UID 초기화 실패: " + e.getMessage());
        }
    }

    // Url Check 기능 확인
    @GetMapping("/url-check-activate")
    public ResponseEntity<Map<String, Boolean>> isUrlCheckActivate() throws Exception {
        long userId = memberService.getUserIdByAuth();
        Boolean isUrlCheckEnabled = redisCacheService.getSettingValue(userId, SettingFeature.URL_CHECK);

        Map<String, Boolean> response = new HashMap<>();
        response.put("isUrlCheckEnabled", isUrlCheckEnabled);

        return ResponseEntity.ok(response);
    }
}
