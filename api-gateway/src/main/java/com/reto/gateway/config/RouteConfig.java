package com.reto.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Value("${inventory-service.base-url}")
    private String inventoryServiceBaseUrl;

    @Value("${order-service.base-url}")
    private String orderServiceBaseUrl;

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("inventory-service", r -> r
                        .path("/api/v1/products/**")
                        .filters(f -> f.dedupeResponseHeader("X-Trace-Id", "RETAIN_FIRST"))
                        .uri(inventoryServiceBaseUrl))
                .route("order-service", r -> r
                        .path("/api/v1/orders/**")
                        .filters(f -> f.dedupeResponseHeader("X-Trace-Id", "RETAIN_FIRST"))
                        .uri(orderServiceBaseUrl))
                .route("inventory-docs", r -> r
                        .path("/docs/inventory/v3/api-docs")
                        .filters(f -> f.setPath("/v3/api-docs"))
                        .uri(inventoryServiceBaseUrl))
                .route("order-docs", r -> r
                        .path("/docs/order/v3/api-docs")
                        .filters(f -> f.setPath("/v3/api-docs"))
                        .uri(orderServiceBaseUrl))
                .build();
    }
}