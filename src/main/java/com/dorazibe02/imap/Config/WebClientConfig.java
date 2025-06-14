package com.dorazibe02.imap.Config;

import com.dorazibe02.imap.Vault.AuthCustomRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    private final AuthCustomRepository authCustomRepository;

    public WebClientConfig(AuthCustomRepository authCustomRepository) {
        this.authCustomRepository = authCustomRepository;
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        // 서비스마다 빌더 생성 안하고 WebClient.Builder 빈으로 제공해서 전역으로 사용
        return WebClient.builder();
    }

    @Bean
    public WebClient webClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.build();
    }
}
