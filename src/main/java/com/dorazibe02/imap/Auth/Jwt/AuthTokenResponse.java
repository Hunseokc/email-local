package com.dorazibe02.imap.Auth.Jwt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthTokenResponse {
    private String accessToken;     // 발급된 JWT
    private String tokenType;       // 토큰 타입 (일반적으로 "Bearer")
    private long expiresIn;         // 토큰 유효 기간 (초 단위)
}
