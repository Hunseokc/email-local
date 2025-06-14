package com.dorazibe02.imap.Auth;

import com.dorazibe02.imap.Auth.Jwt.JwtTokenProvider;
import com.dorazibe02.imap.Auth.Jwt.TokenInfoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public TokenInfoDto login(String email, String password) {
        try {
//            System.out.println("로그인 시도: " + email);
            // 1. Login Email/PW 를 기반으로 Authentication 객체 생성
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(email, password);

            // 2. 실제 검증 (사용자 비밀번호 체크)
            // authenticate 매서드가 실행될 때 CustomUserDetailsService 에서 만든 loadUserByUsername 메서드가 실행
            Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
//            System.out.println("인증 성공");

            // 3. 인증 정보를 기반으로 JWT 생성
            TokenInfoDto tokenInfo = jwtTokenProvider.generateToken(authentication);
//            System.out.println("토큰 생성 성공: " + tokenInfo);

            return tokenInfo;
        } catch (Exception e) {
            e.printStackTrace(); // 콘솔에 실제 예외 메시지 출력
            throw new RuntimeException("로그인 실패: " + e.getMessage(), e);
        }
    }
}
