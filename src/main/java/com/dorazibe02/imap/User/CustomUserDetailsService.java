package com.dorazibe02.imap.User;

import com.dorazibe02.imap.Member.Member;
import com.dorazibe02.imap.Member.MemberRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final MemberRepository memberRepository;

//     JWT 토큰의 인증 계정 찾기
//     MemberRepository에서 인증 계정 찾기
    @Override
    public CustomUserDetail loadUserByUsername(String username) throws UsernameNotFoundException {
        return memberRepository.findByEmail(username)
                .map(this::createUserDetails)
                .orElseThrow(() -> new UsernameNotFoundException("해당하는 유저를 찾을 수 없습니다."));
    }


//   CustomUserDetail 반환 (해당 계정 정보가 존재 시, CustomUserDetail의 형태로 리턴)
    private CustomUserDetail createUserDetails(Member member) {
        Collection<? extends GrantedAuthority> authorities =
                Collections.singleton(new SimpleGrantedAuthority(member.getAccountType().toString()));
        return new CustomUserDetail(
                member.getEmail(),
                member.getLoginpw(),
                authorities,
                member.getId(), // Member PK
                member.getAuth().getId()); // Auth PK 전달
    };
}