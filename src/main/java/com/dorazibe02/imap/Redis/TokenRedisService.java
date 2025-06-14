package com.dorazibe02.imap.Redis;

import com.dorazibe02.imap.Auth.Jwt.JwtTokenProvider;
import com.dorazibe02.imap.Config.RedisConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenRedisService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisConfig redisConfig;

    @Value("${spring.security.jwt.expiration-ms}")
    private long expirationMs;

    public void storeToken(String email, String token) {
        redisConfig.redisTemplate().opsForValue().set(
                email,
                token,
                expirationMs,
                TimeUnit.MILLISECONDS
        );
        Boolean hasToken = redisConfig.redisTemplate().hasKey(email);
        System.out.println("Redis에 저장 여부: "+hasToken);
    }

    public boolean validateToken(String email, String token) {
        String storedToken = redisConfig.redisTemplate().opsForValue().get(email);
        if (!token.equals(storedToken)) return false;

        return jwtTokenProvider.validateToken(token);
    }

    public boolean isToken(String email) {
        Boolean isToken = redisConfig.redisTemplate().hasKey(email);
        return isToken;
    }

    public void revokeToken(String email) {
        redisConfig.redisTemplate().delete(email);
    }
}
