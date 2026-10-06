package com.reto.order.domain.statemachine;

import com.reto.order.domain.model.OrderStatus;
import com.reto.order.exception.InvalidStateTransitionException;
import com.reto.order.exception.OrderAlreadyCancelledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStateMachineTest {

    private OrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine();
    }

    // ---------- Transiciones válidas ----------

    @Test
    @DisplayName("PENDING -> CONFIRMED es una transición permitida")
    void pendingToConfirmed_isAllowed() {
        assertTrue(stateMachine.isTransitionAllowed(OrderStatus.PENDING, OrderStatus.CONFIRMED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(OrderStatus.PENDING, OrderStatus.CONFIRMED));
    }

    @Test
    @DisplayName("PENDING -> FAILED es una transición permitida")
    void pendingToFailed_isAllowed() {
        assertTrue(stateMachine.isTransitionAllowed(OrderStatus.PENDING, OrderStatus.FAILED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(OrderStatus.PENDING, OrderStatus.FAILED));
    }

    @Test
    @DisplayName("PENDING -> CANCELLED es una transición permitida")
    void pendingToCancelled_isAllowed() {
        assertTrue(stateMachine.isTransitionAllowed(OrderStatus.PENDING, OrderStatus.CANCELLED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(OrderStatus.PENDING, OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("CONFIRMED -> CANCELLED es una transición permitida")
    void confirmedToCancelled_isAllowed() {
        assertTrue(stateMachine.isTransitionAllowed(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
    }

    // ---------- Transiciones inválidas ----------

    @Test
    @DisplayName("CONFIRMED -> PENDING no está permitido")
    void confirmedToPending_isNotAllowed() {
        assertFalse(stateMachine.isTransitionAllowed(OrderStatus.CONFIRMED, OrderStatus.PENDING));
        assertThrows(InvalidStateTransitionException.class,
                () -> stateMachine.validateTransition(OrderStatus.CONFIRMED, OrderStatus.PENDING));
    }

    @Test
    @DisplayName("FAILED -> CONFIRMED no está permitido")
    void failedToConfirmed_isNotAllowed() {
        assertFalse(stateMachine.isTransitionAllowed(OrderStatus.FAILED, OrderStatus.CONFIRMED));
        assertThrows(InvalidStateTransitionException.class,
                () -> stateMachine.validateTransition(OrderStatus.FAILED, OrderStatus.CONFIRMED));
    }

    @Test
    @DisplayName("CANCELLED no permite ninguna transición de salida")
    void cancelled_hasNoOutgoingTransitions() {
        assertFalse(stateMachine.isTransitionAllowed(OrderStatus.CANCELLED, OrderStatus.PENDING));
        assertFalse(stateMachine.isTransitionAllowed(OrderStatus.CANCELLED, OrderStatus.CONFIRMED));
        assertFalse(stateMachine.isTransitionAllowed(OrderStatus.CANCELLED, OrderStatus.FAILED));
        assertThrows(InvalidStateTransitionException.class,
                () -> stateMachine.validateTransition(OrderStatus.CANCELLED, OrderStatus.CONFIRMED));
    }

    @Test
    @DisplayName("FAILED no permite ninguna transición de salida")
    void failed_hasNoOutgoingTransitions() {
        assertFalse(stateMachine.isTransitionAllowed(OrderStatus.FAILED, OrderStatus.PENDING));
        assertFalse(stateMachine.isTransitionAllowed(OrderStatus.FAILED, OrderStatus.CANCELLED));
        assertThrows(InvalidStateTransitionException.class,
                () -> stateMachine.validateTransition(OrderStatus.FAILED, OrderStatus.CANCELLED));
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    @DisplayName("Ningún estado permite transicionar a sí mismo")
    void noStatus_allowsTransitionToItself(OrderStatus status) {
        assertFalse(stateMachine.isTransitionAllowed(status, status));
    }

    // ---------- Cancelación (validateCancellation) ----------

    @Test
    @DisplayName("Cancelar un pedido PENDING es válido")
    void validateCancellation_fromPending_doesNotThrow() {
        UUID orderId = UUID.randomUUID();
        assertDoesNotThrow(() -> stateMachine.validateCancellation(orderId, OrderStatus.PENDING));
    }

    @Test
    @DisplayName("Cancelar un pedido CONFIRMED es válido")
    void validateCancellation_fromConfirmed_doesNotThrow() {
        UUID orderId = UUID.randomUUID();
        assertDoesNotThrow(() -> stateMachine.validateCancellation(orderId, OrderStatus.CONFIRMED));
    }

    @Test
    @DisplayName("Cancelar un pedido ya CANCELLED lanza OrderAlreadyCancelledException")
    void validateCancellation_fromCancelled_throwsAlreadyCancelled() {
        UUID orderId = UUID.randomUUID();
        assertThrows(OrderAlreadyCancelledException.class,
                () -> stateMachine.validateCancellation(orderId, OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("Cancelar un pedido FAILED lanza InvalidStateTransitionException")
    void validateCancellation_fromFailed_throwsInvalidTransition() {
        UUID orderId = UUID.randomUUID();
        assertThrows(InvalidStateTransitionException.class,
                () -> stateMachine.validateCancellation(orderId, OrderStatus.FAILED));
    }
}