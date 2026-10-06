package com.reto.inventory.exception;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID productId) {
        super("Producto no encontrado con id: " + productId);
    }
}