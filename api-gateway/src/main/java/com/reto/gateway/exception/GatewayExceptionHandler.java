package com.reto.gateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@Order(-2)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);

        HttpStatus status;
        String code;
        String message;

        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            code = status == HttpStatus.NOT_FOUND ? "RESOURCE_NOT_FOUND" : status.name();
            message = status == HttpStatus.NOT_FOUND
                    ? "El recurso solicitado no existe."
                    : "No fue posible procesar la solicitud.";
        } else if (hasCause(ex, ConnectException.class)) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            code = "SERVICE_UNAVAILABLE";
            message = "El servicio solicitado no está disponible en este momento.";
        } else if (hasCause(ex, TimeoutException.class)
                || hasCause(ex, io.netty.handler.timeout.TimeoutException.class)) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            code = "GATEWAY_TIMEOUT";
            message = "El servicio tardó demasiado en responder.";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = "INTERNAL_ERROR";
            message = "Ocurrió un error interno. Intente nuevamente más tarde.";
        }

        if (status.is5xxServerError()) {
            log.error("[traceId={}] {} {} -> {}", traceId,
                    exchange.getRequest().getMethod(), exchange.getRequest().getURI().getPath(),
                    ex.toString());
        }

        String traceIdJson = traceId == null ? "null" : "\"" + traceId + "\"";
        String body = "{\"timestamp\":\"" + Instant.now() + "\","
                + "\"status\":" + status.value() + ","
                + "\"code\":\"" + code + "\","
                + "\"message\":\"" + message + "\","
                + "\"traceId\":" + traceIdJson + "}";

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        Throwable current = ex;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}