package com.reto.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${inventory-service.base-url}")
    private String inventoryServiceBaseUrl;

    @Bean
    public WebClient inventoryWebClient() {
        return WebClient.builder()
                .baseUrl(inventoryServiceBaseUrl)
                .build();
    }
}