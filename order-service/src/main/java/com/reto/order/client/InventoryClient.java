package com.reto.order.client;

import com.reto.order.client.dto.StockCheckRequest;
import com.reto.order.client.dto.StockCheckResponse;
import com.reto.order.exception.InventoryUnavailableException;
import com.reto.order.util.TraceIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryClient {

    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);
    private static final String UNAVAILABLE_MESSAGE =
            "El servicio de inventario no está disponible en este momento.";

    private final WebClient inventoryWebClient;

    public StockCheckResponse checkAvailability(StockCheckRequest request) {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);

        try {
            return inventoryWebClient.post()
                    .uri("/internal/v1/products/check-availability")
                    .headers(headers -> {
                        if (traceId != null) {
                            headers.set(TraceIdFilter.TRACE_ID_HEADER, traceId);
                        }
                    })
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(StockCheckResponse.class)
                    .timeout(RESPONSE_TIMEOUT)
                    .block();
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().is5xxServerError()) {
                log.error("Inventory respondió con error {}", ex.getStatusCode().value());
                throw new InventoryUnavailableException(UNAVAILABLE_MESSAGE, ex);
            }
            throw ex;
        } catch (WebClientRequestException ex) {
            log.error("No se pudo conectar con Inventory: {}", ex.getMessage());
            throw new InventoryUnavailableException(UNAVAILABLE_MESSAGE, ex);
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof TimeoutException) {
                log.error("Inventory no respondió en {} segundos", RESPONSE_TIMEOUT.toSeconds());
                throw new InventoryUnavailableException(UNAVAILABLE_MESSAGE, ex);
            }
            throw ex;
        }
    }
}