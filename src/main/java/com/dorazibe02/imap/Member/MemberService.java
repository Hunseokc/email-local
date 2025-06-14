package com.dorazibe02.imap.Member;

import com.dorazibe02.imap.Auth.Auth;
import com.dorazibe02.imap.Auth.AuthRepository;
import com.dorazibe02.imap.Notion.NotionEntityService;
import com.dorazibe02.imap.Setting.ThreatAction;
import com.dorazibe02.imap.Setting.UserSetting;
import com.dorazibe02.imap.Setting.UserSettingRepository;
import com.dorazibe02.imap.Redis.TokenRedisService;
import com.dorazibe02.imap.User.CustomUserDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final AuthRepository authRepository;
    private final UserSettingRepository userSettingRepository;

    private final TokenRedisService tokenRedisService;
    private final NotionEntityService notionEntityService;
    private final PasswordEncoder encoder;

    public void createMember(String email, String password, Auth auth) throws Exception {
        Optional<Member> vailMember = memberRepository.findByEmail(email);
        if (vailMember.isPresent()) {
            throw new Exception("This member email is already exist");
        }
        // 비밀번호 해시 처리
        String encodedPassword = encoder.encode(password);

        Member member = new Member();
        member.setEmail(email);
        member.setLoginpw(encodedPassword);
        member.setAccountType(Member.AccountType.USER); // admin 수동 추가
        member.setAuth(auth);
        memberRepository.save(member);

        // 회원 생성 후 UserSetting 기본값으로 저장
        UserSetting newUserSetting = new UserSetting();
        newUserSetting.setAuth(auth);
        newUserSetting.setNotionEnabled(false);
        newUserSetting.setUrlCheckEnabled(true);
        newUserSetting.setFileCheckEnabled(false);
        newUserSetting.setThreatAction(ThreatAction.MOVE_TO_JUNK);
        userSettingRepository.save(newUserSetting);
    }

    // 멤버 삭제
    // 삭제할 db 테이블이 3개 -> 트랜젝션 삭제
    @Transactional
    public void deleteUserByAuthId(Long userId) throws Exception {
        if (userId == null) {
            throw new IllegalArgumentException("userId는 null 일 수 없습니다");
        }

        // 1. Notion 연동 정보 삭제 (Notion 엔티티는 userId로 Auth.id를 사용)
        notionEntityService.deleteNotionDataByAuthId(userId);

        // 2. UserSetting 삭제
        userSettingRepository.findByAuthId(userId).ifPresent(userSettingRepository::delete);

        // 3. Member 정보 삭제 (Member : Auth = 1:1 관계 => authId 조회)
        memberRepository.findByAuthId(userId).ifPresent(member -> {
            // Member 삭제하기 전에 Redis 토큰 있으면 삭제
            tokenRedisService.revokeToken(member.getEmail());
            memberRepository.delete(member);
        });


        // 4. Auth 정보 삭제
        authRepository.findById(userId).ifPresent(authRepository::delete);

        System.out.println("User ID " + userId + " 사용자의 모든 관련 정보가 삭제되었습니다.");
    }

    // 비밀번호 db 업데이트(비밀번호 변경)
    public void resetLoginPw(String email, String password) throws Exception {
        // 비밀번호 해시 처리
        String encodedPassword = encoder.encode(password);

        Member member = new Member();
        member.setEmail(email);
        member.setLoginpw(encodedPassword);
        memberRepository.save(member);
    }

    // 비밀번호 변경 기능 구현 시 사용
    public boolean certHashPw(String email, String password) throws Exception {
        Optional<Member> vailMember = memberRepository.findByEmail(email);
        if (vailMember.isPresent()) {
            // 비밀번호 변경시 현재 비밀번호 입력 검증
            String encodedPassword = encoder.encode(password);
            if (vailMember.get().getLoginpw().equals(encodedPassword)) {
                return true;
            } else return false;
        }
        System.out.println("오류 : 현재 로그인한 사용자가 DB에 존재하지 않습니다");
        return false;
    }

    public long getUserIdByEmail(String email) throws Exception {
        Member member = memberRepository.findByEmail(email).orElseThrow(
                () -> new RuntimeException("Member not found with email: " + email));
        return member.getAuth().getId();
    }

    public String getUserIdStrByAuth() throws Exception {
        CustomUserDetail userDetail = getUserDetail();
        if (userDetail == null) {
            throw new RuntimeException("User not authenticated.");
        }
        return String.valueOf(userDetail.getAuthId());
    }
    public long getUserIdByAuth() throws Exception {
        CustomUserDetail userDetail = getUserDetail();
        if (userDetail == null) {
            throw new RuntimeException("User not authenticated.");
        }
        return userDetail.getAuthId();
    }

    public CustomUserDetail getUserDetail() throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetail) {
            return (CustomUserDetail) principal;
        }
        return null;
    }
}