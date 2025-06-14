package com.dorazibe02.imap.Vault;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuthCustomRepositoryImpl implements AuthCustomRepository {

    private final EntityManager entityManager;

    @Override
    public String getSecretFromVault(String keyName) {
        Query query = entityManager.createNativeQuery("SELECT get_api_key(:keyName)");
        query.setParameter("keyName", keyName);
        return (String) query.getSingleResult();
    }
}
