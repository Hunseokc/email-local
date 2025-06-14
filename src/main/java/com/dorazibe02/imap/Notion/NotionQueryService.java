package com.dorazibe02.imap.Notion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotionQueryService {
    private final NotionRepository notionRepository;

    public boolean isConnect(long userId) throws Exception {
        Optional<Notion> notionOptional = notionRepository.findByAuthId(userId);
        return notionOptional.isPresent();
    }

    public Notion getNotionInfo(long userId) throws Exception {
        // Notion 정보가 없을 경우 RuntimeException 발생
        Notion notion = notionRepository.findByAuthId(userId)
                .orElseThrow(() -> new RuntimeException("User " + userId + " does not have Notion integration configured."));
        return notion;
    }
}
