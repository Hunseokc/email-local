package com.dorazibe02.imap.Controller;

import com.dorazibe02.imap.Auth.Auth;
import com.dorazibe02.imap.Auth.AuthService;
import com.dorazibe02.imap.Auth.Jwt.TokenInfoDto;
import com.dorazibe02.imap.Redis.TokenRedisService;
import com.dorazibe02.imap.Member.Member;
import com.dorazibe02.imap.Member.MemberRepository;
import com.dorazibe02.imap.Member.MemberService;
import com.dorazibe02.imap.Auth.UserAuthCredentialService;
import com.dorazibe02.imap.Email.EmailService;
import com.dorazibe02.imap.User.CustomUserDetail;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.Properties;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthRestController {
    private final MemberRepository memberRepository;
    private final TokenRedisService redisService;
    private final MemberService memberService;
    private final AuthService authService;
    private final EmailService emailService;
    private final UserAuthCredentialService userAuthCredentialService;

    public record UserRegisterData(String email, String password, String loginpw) {}
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserRegisterData userData) {
        try {
            log.info("register 요청");
            String email = userData.email();
            Optional<Member> result = memberRepository.findByEmail(email);
            if (result.isPresent()) {
                throw new Exception("존재하는 아이디입니다.");
            } else if (!userData.loginpw().matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()\\-_=+{};:,<.>]).{8,25}$")) {
                throw new IllegalArgumentException("비밀번호는 영문, 숫자, 특수문자를 포함한 8~25자리여야 합니다");
            }

            // 1. 이메일 도메인 기반 IMAP 호스트 추출 (예: naver.com → imap.naver.com)
            String host = emailService.resolveImapHost(email);

            // 2. IMAP 서버 연결을 통해 email/pw 검증
            if (!ImapAuthValidator.validate(email, userData.password(), host)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("IMAP 인증 실패");
            }
            log.info("IMAP 인증 성공");

            // 3. 인증 성공 시 id pw 저장
            Auth auth = userAuthCredentialService.createAuthDetail(email, userData.password());
            memberService.createMember(email, userData.loginpw(), auth);
            log.info("member 저장 성공");

            return ResponseEntity.status(HttpStatus.OK).body("success");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public record UserData(String email, String password) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserData userData, HttpServletResponse response) {
        try {
            log.info("Login POST 요청");
            // 연결 수립 - jwt 토큰 발급
            TokenInfoDto tokenInfo = authService.login(userData.email, userData.password); // 토큰 생성

            redisService.storeToken(userData.email, tokenInfo.getAccessToken());

            // JWT를 HttpOnly 쿠키에 저장
            ResponseCookie cookie = ResponseCookie.from("accessToken", tokenInfo.getAccessToken())
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(60*20)
                    .build();

            response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());

            return ResponseEntity.status(HttpStatus.OK).body(tokenInfo);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }
    }

    @PostMapping("/check-user")
    public ResponseEntity<?> checkUser(@RequestBody UserRegisterData userData) {
        try {
            String email = userData.email;
            Optional<Member> result = memberRepository.findByEmail(email);
            if (!result.isPresent()) {
                throw new Exception("존재하지 않는 아이디입니다.");
            } else {
                // 이메일 도메인 기반 IMAP 호스트 추출 (예: naver.com → imap.naver.com)
                String host = emailService.resolveImapHost(email);

                // IMAP 서버 연결을 통해 email / IMAP pw 검증
                if (!ImapAuthValidator.validate(email, userData.password, host)) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("IMAP 인증 실패");
                }
                log.info("IMAP 인증 성공");
            }

            return ResponseEntity.status(HttpStatus.OK).body("success");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("잘못된 요청: " + e.getMessage());
        } catch (Exception e) {
            log.error("유저 확인 오류: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("유저 확인 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPw(@RequestBody UserRegisterData userData) {
        try {
            // html 조작 방지 (강제로 패스워드 재설정 버튼 표기 방지를 위해 IMAP 연결 재확인)
            String host = emailService.resolveImapHost(userData.email);
            if (!ImapAuthValidator.validate(userData.email, userData.password, host)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("IMAP 인증 실패");
            }

            // 인증 성공 시 login pw 재설정 가능
            if (!userData.password.matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()\\-_=+{};:,<.>]).{8,25}$")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("새 비밀번호는 영문, 숫자, 특수문자를 포함한 8~25자리여야 합니다.");
            } else {
                memberService.resetLoginPw(userData.email, userData.loginpw);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        return ResponseEntity.status(HttpStatus.OK).body("success");
    }

    public record changePwRecord(String currentPw, String NewPw) {}
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody changePwRecord data, @AuthenticationPrincipal CustomUserDetail userDetails) throws Exception {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증되지 않은 사용자입니다.");
        }

        String email = userDetails.getEmail();
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        try {
            if (memberService.certHashPw(email, data.currentPw())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("현재 비밀번호가 일치하지 않습니다.");
            }

            // 인증 성공 시 login pw 재설정 가능
            if (!data.NewPw().matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()\\-_=+{};:,<.>]).{8,25}$")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("새 비밀번호는 영문, 숫자, 특수문자를 포함한 8~25자리여야 합니다.");
            } else {
                memberService.resetLoginPw(email, data.NewPw());
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        return ResponseEntity.status(HttpStatus.OK).body("success");
    }

    @DeleteMapping("/delete-account")
    public ResponseEntity<String> deleteAccount(@AuthenticationPrincipal CustomUserDetail userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증되지 않은 사용자입니다.");
        }

        try {
            memberService.deleteUserByAuthId(userDetails.getAuthId());
            return ResponseEntity.ok("회원 탈퇴가 성공적으로 처리되었습니다.");
        } catch (Exception e) {
            log.error("회원 탈퇴 처리 중 오류 발생 (User: {}): {}", userDetails.getEmail(), e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("회원 탈퇴 처리 중 오류가 발생했습니다.");
        }
    }

    private class ImapAuthValidator {
        public static boolean validate(String email, String password, String host) {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imaps.host", host);
            props.put("mail.imaps.port", "993");
            props.put("mail.imaps.ssl.enable", "true");

            try {
                Session session = Session.getInstance(props);
                Store store = session.getStore("imaps");
                store.connect(host, email, password); // 연결 시도
                store.close();
                return true;
            } catch (AuthenticationFailedException e) {
                log.error("인증 실패: {}", e.getMessage());
                return false; // 인증 실패
            } catch (Exception e) {
                throw new RuntimeException("IMAP 연결 오류", e);
            }
        }
    }
}
