package com.reto.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCheckItemResult {

    private String productId;
    private boolean available;
    private Integer availableStock;
    private String productName;
    private BigDecimal unitPrice;
}