package com.dorazibe02.imap.User;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

public class CustomUserDetail extends User {

    //username(Email), password, authorities
    private final Long memberId;    // PK
    private final Long authId;
    private String email;

    public CustomUserDetail(String email, String password, Collection<? extends GrantedAuthority> authorities, long memberId, long authId) {
        super(email, password, authorities);
        this.memberId = memberId;
        this.email = email;
        this.authId = authId;
    }

    public Long getUserId() {
        return authId;
    }
    public String getEmail() { return email; }
    public Long getAuthId() { return authId; }
}