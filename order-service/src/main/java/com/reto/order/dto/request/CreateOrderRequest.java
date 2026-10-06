package com.reto.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotBlank(message = "El customerId es obligatorio.")
    private String customerId;

    @NotEmpty(message = "El pedido debe tener al menos un item.")
    private List<@Valid CreateOrderItemRequest> items;
}