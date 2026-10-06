package com.reto.order.exception;

import com.reto.order.domain.model.OrderStatus;

public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(OrderStatus currentStatus, OrderStatus targetStatus) {
        super("Transición inválida de " + currentStatus + " a " + targetStatus + ".");
    }
}