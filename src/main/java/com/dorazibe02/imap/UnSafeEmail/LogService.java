package com.dorazibe02.imap.UnSafeEmail;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LogService {

    private final UnSafeEmailRepository unSafeEmailRepository;

    public List<UnSafeEmailDto> getRecentThreats(Long authId) {

        ZonedDateTime sevenDaysAgo = ZonedDateTime.now().minusDays(7);
        String sevenDaysAgoString = sevenDaysAgo.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        // 최대 15개
        List<UnSafeEmail> recentThreats = unSafeEmailRepository.findTop15ByAuthIdAndReceivedDateAfterOrderByReceivedDateDesc(
                authId,
                sevenDaysAgoString
        );

        return recentThreats.stream()
                .map(UnSafeEmailDto::new)
                .collect(Collectors.toList());
    }
}
