package com.reto.order.controller;

import com.reto.order.dto.request.CreateOrderRequest;
import com.reto.order.dto.response.ErrorResponse;
import com.reto.order.dto.response.OrderHistoryResponse;
import com.reto.order.dto.response.OrderResponse;
import com.reto.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Pedidos", description = "Registro, consulta, historial y cancelación de pedidos")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Listar todos los pedidos")
    @ApiResponse(responseCode = "200", description = "Lista de pedidos (vacía si no hay ninguno)")
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        List<OrderResponse> response = orderService.getAllOrders();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Registrar un pedido",
            description = "Consulta disponibilidad en Inventory. Con stock queda CONFIRMED; sin stock se guarda como FAILED y responde 409.")
    @ApiResponse(responseCode = "201", description = "Pedido creado y confirmado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (VALIDATION_ERROR)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Stock insuficiente (STOCK_INSUFFICIENT)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Inventory no disponible (INVENTORY_UNAVAILABLE)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Consultar un pedido y su estado")
    @ApiResponse(responseCode = "200", description = "Pedido encontrado")
    @ApiResponse(responseCode = "400", description = "El id no tiene formato UUID (INVALID_REQUEST)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "El pedido no existe (ORDER_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID orderId) {
        OrderResponse response = orderService.getOrderById(orderId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Consultar el historial de estados de un pedido",
            description = "Entradas ordenadas cronológicamente por sequenceNumber.")
    @ApiResponse(responseCode = "200", description = "Historial del pedido")
    @ApiResponse(responseCode = "404", description = "El pedido no existe (ORDER_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{orderId}/history")
    public ResponseEntity<OrderHistoryResponse> getOrderHistory(@PathVariable UUID orderId) {
        OrderHistoryResponse response = orderService.getOrderHistory(orderId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cancelar un pedido",
            description = "Permitido desde PENDING o CONFIRMED. FAILED y CANCELLED no se pueden cancelar.")
    @ApiResponse(responseCode = "200", description = "Pedido cancelado")
    @ApiResponse(responseCode = "404", description = "El pedido no existe (ORDER_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409",
            description = "Ya estaba cancelado (ORDER_ALREADY_CANCELLED) o transición inválida (INVALID_STATE_TRANSITION)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID orderId) {
        OrderResponse response = orderService.cancelOrder(orderId);
        return ResponseEntity.ok(response);
    }
}