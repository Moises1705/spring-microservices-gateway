package com.reto.order.domain.statemachine;

import com.reto.order.domain.model.OrderStatus;
import com.reto.order.exception.InvalidStateTransitionException;
import com.reto.order.exception.OrderAlreadyCancelledException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(
                OrderStatus.CONFIRMED,
                OrderStatus.FAILED,
                OrderStatus.CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(OrderStatus.CONFIRMED, EnumSet.of(
                OrderStatus.CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.FAILED, EnumSet.noneOf(OrderStatus.class));
    }

    public boolean isTransitionAllowed(OrderStatus currentStatus, OrderStatus targetStatus) {
        return ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of()).contains(targetStatus);
    }

    public void validateTransition(OrderStatus currentStatus, OrderStatus targetStatus) {
        if (!isTransitionAllowed(currentStatus, targetStatus)) {
            throw new InvalidStateTransitionException(currentStatus, targetStatus);
        }
    }

    public void validateCancellation(UUID orderId, OrderStatus currentStatus) {
        if (currentStatus == OrderStatus.CANCELLED) {
            throw new OrderAlreadyCancelledException(orderId);
        }
        validateTransition(currentStatus, OrderStatus.CANCELLED);
    }
}