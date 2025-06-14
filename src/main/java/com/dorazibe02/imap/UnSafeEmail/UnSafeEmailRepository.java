package com.dorazibe02.imap.UnSafeEmail;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnSafeEmailRepository extends JpaRepository<UnSafeEmail, Long> {
    // 특정 사용자의, 특정 날짜 이후의 로그를 최신순으로 상위 N개만 가져오는 쿼리 메소드
    List<UnSafeEmail> findTop15ByAuthIdAndReceivedDateAfterOrderByReceivedDateDesc(Long authId, String sevenDaysAgo);
}
