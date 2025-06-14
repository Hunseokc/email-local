package com.dorazibe02.imap.Auth.Jwt;

import com.dorazibe02.imap.User.CustomUserDetail;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${spring.security.jwt.expiration-ms}")
    private long expirationMs;

    @Value("${spring.security.jwt.secret}")
    private String jwtSecret;

    private Key key;

    @PostConstruct
    public void init() {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
            this.key = Keys.hmacShaKeyFor(keyBytes);
            log.info("JWT Secret loaded successfully.");
        } catch (Exception e) {
            log.error("Failed to load JWT Secret: {}", e.getMessage());
            throw new RuntimeException("Failed to load JWT Secret.", e);
        }
    }

    public TokenInfoDto generateToken(Authentication authentication) {

        // 권한 가져오기
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        CustomUserDetail userDetails = (CustomUserDetail) authentication.getPrincipal();
        Long memberId = userDetails.getUserId();
        Long authId = userDetails.getAuthId();

        long now = (new Date()).getTime();

        // Access Token 생성
        Date accessTokenExpiresIn = new Date(now + expirationMs);
        String accessToken = Jwts.builder()
                .setSubject(authentication.getName())
                .claim("memberId", memberId)
                .claim("authId", authId)
                .claim("auth", authorities)
                .setExpiration(accessTokenExpiresIn)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        return TokenInfoDto.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .build();
    }

        // JWT 복호화
    public Authentication getAuthentication(String accessToken) {
        Claims claims = parseClaims(accessToken);

        if (claims.get("auth") == null) {
            throw new RuntimeException("권한 정보가 없는 토큰입니다.");
        }
        // 클레임에서 권한 정보 가져오기
        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get("auth").toString().split(","))
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

        // UserDetails 객체 만들어서 Authentication 리턴
        // memberId / authId -> claims에서 가져와 CustomUserDetail 생성자에 전달
        Long memberId = claims.get("memberId") != null ? Long.valueOf((Integer)claims.get("memberId")) : null;
        Long authId = claims.get("authId") != null ? Long.valueOf((Integer)claims.get("authId")) : null;

        if (memberId == null || authId == null) {
            throw new RuntimeException("토큰에 Member ID 또는 Auth ID 정보가 없습니다.");
        }

        CustomUserDetail userDetails = new CustomUserDetail(
                claims.getSubject(), // email
                "", // password (토큰에는 비밀번호 없음)
                authorities,
                memberId,
                authId);
        return new UsernamePasswordAuthenticationToken(userDetails, "", authorities);
    }


    // JWT 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("Invalid JWT Token", e);
        } catch (ExpiredJwtException e) {
            log.info("Expired JWT Token", e);
        } catch (UnsupportedJwtException e) {
            log.info("Unsupported JWT Token", e);
        } catch (IllegalArgumentException e) {
            log.info("JWT claims string is empty.", e);
        }
        return false;
    }

    // JWT 클래임 추출
    private Claims parseClaims(String accessToken) {
        try {
            return Jwts.parser().setSigningKey(key).build().parseClaimsJws(accessToken).getBody();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    public String getEmailFromToken(String accessToken) {
        // 토큰 복호화
        try {
            Claims claims =  Jwts.parser().setSigningKey(key).build().parseClaimsJws(accessToken).getPayload();
            return claims.getSubject();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getSubject();
        }
    }
}
