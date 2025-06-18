package com.dorazibe02.imap.Controller;

import com.dorazibe02.imap.Notion.NotionQueryService;
import com.dorazibe02.imap.Redis.RedisCacheService;
import com.dorazibe02.imap.Setting.SettingFeature;
import com.dorazibe02.imap.Setting.ThreatAction;
import com.dorazibe02.imap.User.CustomUserDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/setting")
public class SettingRestController {
    private final NotionQueryService notionQueryService;
    private final RedisCacheService redisCacheService;

    public record SettingChangeRequest(String feature, boolean enabled) {}
    @PostMapping("/set-value")
    public ResponseEntity<?> setEnable(@RequestBody SettingChangeRequest request) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증 필요");
            }
            CustomUserDetail userDetails = (CustomUserDetail) authentication.getPrincipal();
            long userId = userDetails.getUserId();

            SettingFeature feature = SettingFeature.valueOf(request.feature.toUpperCase());

            // 들어온 입력이 켜기일 때
            if (request.enabled) {
                // 노션은 연동 필요하니 연동 검사 (노션 컨트롤러에 들어가야 하지만 코드 중복으로 Email 컨트롤러로 통합)
                if (feature == SettingFeature.NOTION_ENABLED) {
                    if (!notionQueryService.isConnect(userId)) {
                        Map<String, String> errorResponse = new HashMap<>();
                        errorResponse.put("errorCode", "NOTION_NOT_CONNECTED");
                        errorResponse.put("message", "Notion 연동 정보가 없습니다. 먼저 연동을 설정해주세요."); // 프론트 alert

                        // 409 Conflict: 요청이 현재 리소스의 상태와 충돌
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
                    }
                }
            }
            redisCacheService.setSettingValue(userId, feature, request.enabled);

            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("feature", feature.name());
            successResponse.put("isEnabled", request.enabled);
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public record ThreatActionRequest(String action) {}

    @PostMapping("/threat-action")
    public ResponseEntity<?> setThreatAction(@RequestBody ThreatActionRequest request) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증 필요");
            }

            CustomUserDetail userDetails = (CustomUserDetail) authentication.getPrincipal();
            long userId = userDetails.getUserId();

            ThreatAction newAction = ThreatAction.valueOf(request.action());

            redisCacheService.setThreatAction(userId, newAction);

            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("nowAction", newAction);
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
