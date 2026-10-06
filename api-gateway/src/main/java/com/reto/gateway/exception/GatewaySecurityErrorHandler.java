package com.reto.gateway.exception;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class GatewaySecurityErrorHandler implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return write(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_INVALID_OR_EXPIRED",
                "El token es inválido, ha expirado o no fue enviado.");
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return write(exchange, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "No tiene permisos para acceder a este recurso.");
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        String traceIdJson = traceId == null ? "null" : "\"" + traceId + "\"";

        String body = "{\"timestamp\":\"" + Instant.now() + "\","
                + "\"status\":" + status.value() + ","
                + "\"code\":\"" + code + "\","
                + "\"message\":\"" + message + "\","
                + "\"traceId\":" + traceIdJson + "}";

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}