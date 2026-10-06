package com.reto.inventory.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    @NotBlank(message = "El sku es obligatorio.")
    private String sku;

    @NotBlank(message = "El nombre es obligatorio.")
    private String name;

    @NotNull(message = "El stock disponible es obligatorio.")
    @Min(value = 0, message = "El stock disponible no puede ser negativo.")
    private Integer stockAvailable;

    @NotNull(message = "El precio es obligatorio.")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a 0.")
    private BigDecimal price;
}