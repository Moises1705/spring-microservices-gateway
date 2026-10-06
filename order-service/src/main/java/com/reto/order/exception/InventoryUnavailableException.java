package com.reto.order.exception;

public class InventoryUnavailableException extends RuntimeException {

    public InventoryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}