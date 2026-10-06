package com.reto.inventory.exception;

public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(String sku) {
        super("Ya existe un producto con sku: " + sku);
    }
}