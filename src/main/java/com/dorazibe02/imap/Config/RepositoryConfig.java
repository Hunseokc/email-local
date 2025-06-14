package com.dorazibe02.imap.Config;

import com.dorazibe02.imap.Vault.AuthCustomRepository;
import com.dorazibe02.imap.Vault.AuthCustomRepositoryImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.dorazibe02.imap") // 실제 리포지토리 패키지
public class RepositoryConfig {

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    public AuthCustomRepository authCustomRepository() {
        return new AuthCustomRepositoryImpl(entityManager); // 구현체 필요
    }
}