package com.dorazibe02.imap.Notion;

import com.dorazibe02.imap.Auth.Auth;
import com.dorazibe02.imap.Auth.AuthRepository;
import com.dorazibe02.imap.CryptoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotionEntityService {
    private final NotionRepository notionRepository;
    private final AuthRepository authRepository;

    public void createMember(String email, String key, String dbId, long userId) throws Exception {
        Optional<Auth> optionalAuth = authRepository.findById(userId);
        Auth auth = optionalAuth.orElseThrow(() -> new RuntimeException("Auth not found for id: " + userId));

        // 암호화
        String encryptKey = CryptoUtil.encrypt(key);

        Notion notion = new Notion();
        notion.setEmail(email);
        notion.setApi(encryptKey);
        notion.setNotionDbId(dbId);
        notion.setAuth(auth);
        notionRepository.save(notion);
    }

    @Transactional
    public void deleteNotionDataByAuthId(Long userId) throws Exception {
        if (userId == null) {
            throw new IllegalArgumentException("userId는 null 일 수 없습니다");
        }

        // 1. Notion 연동 정보 삭제 (Notion 엔티티는 userId로 Auth.id를 사용)
        notionRepository.findByAuthId(userId).ifPresent(notionRepository::delete);
    }

    public String getDecryptedApiSecret(long userId) throws Exception {
        // Notion 정보가 없을 경우 RuntimeException 발생
        Notion notion = notionRepository.findByAuthId(userId)
                .orElseThrow(() -> new RuntimeException("User " + userId + " does not have Notion integration configured."));
        return CryptoUtil.decrypt(notion.getApi());
    }
}
