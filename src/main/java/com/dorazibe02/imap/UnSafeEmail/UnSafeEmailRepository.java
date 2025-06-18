package com.dorazibe02.imap.UnSafeEmail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface UnSafeEmailRepository extends JpaRepository<UnSafeEmail, Long> {
    // 특정 사용자의, 특정 날짜 이후의 로그를 최신순으로 상위 N개만 가져오는 쿼리 메소드
    List<UnSafeEmail> findTop15ByAuthIdAndReceivedDateAfterOrderByReceivedDateDesc(Long authId, String sevenDaysAgo);

    @Modifying
    @Transactional
    @Query("DELETE FROM UnSafeEmail u WHERE u.receivedDate < :cutoffDate")
    void deleteOlderThan(String cutoffDate);

    @Modifying
    @Transactional
    @Query("DELETE FROM UnSafeEmail ue WHERE ue.auth.id = :authId")
    void deleteByAuthId(@Param("authId") Long authId);
}
