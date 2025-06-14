package com.dorazibe02.imap.Auth.Jwt;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;
import org.springframework.lang.Nullable;

@Data
@RedisHash(value = "FCMToken", timeToLive = 30)
public class JwtToken {
    @Id
    private String jwtToken;
    @Indexed
    private Long memberPK;
    @Indexed
    @Nullable
    private String fireBaseToken;
    @TimeToLive
    private Long ttl;

    @Builder
    public JwtToken(String jwtToken, Long memberPK, String fireBaseToken, Long ttl) {
        this.jwtToken = jwtToken;
        this.memberPK = memberPK;
        this.fireBaseToken = fireBaseToken;
        this.ttl = ttl;
    }
}
