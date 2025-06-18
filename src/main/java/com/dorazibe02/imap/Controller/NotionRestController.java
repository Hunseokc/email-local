package com.dorazibe02.imap.Controller;

import com.dorazibe02.imap.Notion.EmailMessage;
import com.dorazibe02.imap.Notion.NotionQueryService;
import com.dorazibe02.imap.Notion.NotionService;
import com.dorazibe02.imap.Redis.RedisCacheService;
import com.dorazibe02.imap.Member.MemberService;
import com.dorazibe02.imap.Notion.NotionEntityService;
import com.dorazibe02.imap.Setting.SettingFeature;
import com.dorazibe02.imap.User.CustomUserDetail;
import lombok.RequiredArgsConstructor;
import org.aspectj.weaver.ast.Not;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;


import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/notion")
public class NotionRestController {
    private final MemberService memberService;

    private final NotionService notionService;
    private final NotionQueryService notionQueryService;
    private final NotionEntityService notionEntityService;
    private final RedisCacheService redisCacheService;

    // 연동 확인
    @GetMapping("/check-activate")
    public ResponseEntity<Map<String, Boolean>> isActivate() throws Exception {
        // 로그인 정보로 userId 획득 후 노션 연동 확인
        long userId = memberService.getUserIdByAuth();
        boolean isNotionConnect = notionQueryService.isConnect(userId);

        Map<String, Boolean> response = new HashMap<>();

        if (isNotionConnect) {
            boolean isEnabled = redisCacheService.getSettingValue(userId, SettingFeature.NOTION_ENABLED);
            response.put("isNotionConnect", isNotionConnect);
            response.put("isEnabled", isEnabled);
        }

        return ResponseEntity.ok(response);
    }

    public record NotionData(String apikey, String dbid) {}
    // 연동 정보 생성
    @PostMapping("/create-user")
    public ResponseEntity<?> active(@RequestBody NotionData notionData) throws Exception {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
               return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증 필요");
            }

            System.out.println("노션 데이터 저장 시작");
            CustomUserDetail userDetails = (CustomUserDetail) authentication.getPrincipal();
            String email = userDetails.getEmail();
            long userId = userDetails.getUserId();

            // API Key
            notionEntityService.createMember(email, notionData.apikey, notionData.dbid, userId);
            System.out.println("api key & DB id 저장 완료");

            redisCacheService.setSettingValue(userId, SettingFeature.NOTION_ENABLED, true);

            String message = "save success";
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @DeleteMapping("/delete-user")
    public ResponseEntity<String> deleteAccount(@AuthenticationPrincipal CustomUserDetail userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증되지 않은 사용자입니다.");
        }

        try {
            notionEntityService.deleteNotionDataByAuthId(userDetails.getAuthId());
            return ResponseEntity.ok("노션 연동 정보 삭제가 성공적으로 처리되었습니다.");
        } catch (Exception e) {
            System.err.println("노션 연동 정보 삭제 처리 중 오류 발생 (User: " + userDetails.getEmail() + "): " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("노션 연동 정보 삭제 처리 중 오류가 발생했습니다.");
        }
    }

    @GetMapping("/get-notion-id")
    public ResponseEntity<?> update(@RequestBody NotionData notionData) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증 필요");
            }

            System.out.println("노션 데이터 업데이트 시작");
            CustomUserDetail userDetails = (CustomUserDetail) authentication.getPrincipal();

            long userId = userDetails.getUserId();

            String dbId = notionQueryService.getNotionInfo(userId).getNotionDbId();

            Map<String, String> response = new HashMap<>();
            response.put("dbid", dbId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public record NotionTestRequest(String apikey, String dbid) {}
    @PostMapping("/test-connection")
    public ResponseEntity<?> testNotionConnection(@RequestBody NotionTestRequest request) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증 필요");
            } else {
                String apiKeyToTest = request.apikey();
                if (request.apikey().isEmpty()) {
                    CustomUserDetail userDetails = (CustomUserDetail) authentication.getPrincipal();
                    long userId = userDetails.getUserId();

                    apiKeyToTest = notionEntityService.getDecryptedApiSecret(userId);
                }
                notionService.testCreatePage(apiKeyToTest, request.dbid());
            }

            return ResponseEntity.ok().body("연결에 성공했습니다! Notion에서 '[테스트] Notion 연동 성공' 페이지를 확인하세요.");
        } catch (WebClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();
            String errorMessage = "연결 실패: ";
            if (errorBody.contains("Invalid API key")) {
                errorMessage += "API 키가 잘못되었습니다.";
            } else if (errorBody.contains("Could not find database")) {
                errorMessage += "데이터베이스 ID를 찾을 수 없거나, 통합(Integration)에 대한 권한이 없습니다.";
            } else {
                errorMessage += "알 수 없는 오류가 발생했습니다. 입력값을 확인해주세요.";
            }
            return ResponseEntity.status(e.getStatusCode()).body(errorMessage);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 내부 오류: " + e.getMessage());
        }
    }

    private EmailMessage testEmail() {
        // 테스트용 이메일 메시지 객체
        return new EmailMessage(
                "[테스트] Notion 연동 성공",
                "Email-Plugin-System",
                ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                List.of("Test"),
                "Notion API 연동 TEST 성공 메시지",
                "Read"
        );
    }
}
