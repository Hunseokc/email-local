package com.dorazibe02.imap.Vault;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthCustomRepository { // JpaRepository 상속 없이 단순 리포지토리로
    @Query(value = "SELECT get_api_key(:keyName)", nativeQuery = true)
    String getSecretFromVault(@Param("keyName") String keyName);
}
