package com.reto.order.exception;

import java.util.UUID;

public class OrderAlreadyCancelledException extends RuntimeException {

    public OrderAlreadyCancelledException(UUID orderId) {
        super("El pedido " + orderId + " ya se encuentra cancelado.");
    }
}