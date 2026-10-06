package com.reto.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdWebFilter implements WebFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_ATTRIBUTE = "traceId";

    private static final Pattern VALID_TRACE_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incomingTraceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        String traceId = isValid(incomingTraceId) ? incomingTraceId : UUID.randomUUID().toString();

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(TRACE_ID_HEADER, traceId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
        mutatedExchange.getAttributes().put(TRACE_ID_ATTRIBUTE, traceId);
        mutatedExchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);

        log.info("[traceId={}] --> {} {}", traceId,
                mutatedRequest.getMethod(), mutatedRequest.getURI().getPath());

        return chain.filter(mutatedExchange)
                .then(Mono.fromRunnable(() -> log.info("[traceId={}] <-- {} {}", traceId,
                        mutatedExchange.getResponse().getStatusCode(),
                        mutatedRequest.getURI().getPath())));
    }

    private boolean isValid(String traceId) {
        return traceId != null && VALID_TRACE_ID.matcher(traceId).matches();
    }
}