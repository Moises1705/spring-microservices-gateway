package com.reto.order.service.impl;

import com.reto.order.client.InventoryClient;
import com.reto.order.client.dto.StockCheckItemResult;
import com.reto.order.client.dto.StockCheckRequest;
import com.reto.order.client.dto.StockCheckResponse;
import com.reto.order.domain.model.Order;
import com.reto.order.domain.model.OrderStatus;
import com.reto.order.domain.model.OrderStatusHistory;
import com.reto.order.domain.repository.OrderRepository;
import com.reto.order.domain.repository.OrderStatusHistoryRepository;
import com.reto.order.domain.statemachine.OrderStateMachine;
import com.reto.order.dto.request.CreateOrderItemRequest;
import com.reto.order.dto.request.CreateOrderRequest;
import com.reto.order.dto.response.OrderHistoryEntryResponse;
import com.reto.order.dto.response.OrderHistoryResponse;
import com.reto.order.dto.response.OrderResponse;
import com.reto.order.exception.InvalidStateTransitionException;
import com.reto.order.exception.InventoryUnavailableException;
import com.reto.order.exception.OrderAlreadyCancelledException;
import com.reto.order.exception.OrderNotFoundException;
import com.reto.order.exception.StockInsufficientException;
import com.reto.order.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private OrderMapper orderMapper;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        // Se usa la máquina de estados real: es lógica pura y así se prueba la integración con el servicio.
        orderService = new OrderServiceImpl(
                orderRepository,
                orderStatusHistoryRepository,
                inventoryClient,
                new OrderStateMachine(),
                orderMapper
        );
    }

    // ------------------------------------------------------------------
    // createOrder
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Con stock suficiente el pedido queda CONFIRMED con historial PENDING -> CONFIRMED")
    void createOrder_withStock_isConfirmed() {
        CreateOrderRequest request = requestFor("prod-1", 2);
        when(inventoryClient.checkAvailability(any(StockCheckRequest.class)))
                .thenReturn(stockResponse(availableItem("prod-1", 10, "Teclado", "25.00")));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderMapper.toResponse(any(Order.class)))
                .thenReturn(OrderResponse.builder().status("CONFIRMED").build());

        OrderResponse response = orderService.createOrder(request);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();

        assertEquals("CONFIRMED", response.getStatus());
        assertEquals(OrderStatus.CONFIRMED, saved.getStatus());
        assertEquals("c-1", saved.getCustomerId());
        assertEquals(0, new BigDecimal("50.00").compareTo(saved.getTotalAmount()));
        assertEquals(1, saved.getItems().size());
        assertEquals("Teclado", saved.getItems().get(0).getProductName());

        List<OrderStatusHistory> history = saved.getStatusHistory();
        assertEquals(2, history.size());
        assertEquals(1, history.get(0).getSequenceNumber());
        assertNull(history.get(0).getPreviousStatus());
        assertEquals(OrderStatus.PENDING, history.get(0).getNewStatus());
        assertEquals(2, history.get(1).getSequenceNumber());
        assertEquals(OrderStatus.PENDING, history.get(1).getPreviousStatus());
        assertEquals(OrderStatus.CONFIRMED, history.get(1).getNewStatus());
        assertNull(history.get(1).getReason());
    }

    @Test
    @DisplayName("Sin stock el pedido se guarda como FAILED y se lanza StockInsufficientException")
    void createOrder_withoutStock_isFailedAndThrows() {
        CreateOrderRequest request = requestFor("prod-1", 50);
        when(inventoryClient.checkAvailability(any(StockCheckRequest.class)))
                .thenReturn(stockResponse(unavailableItem("prod-1", 3)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(StockInsufficientException.class, () -> orderService.createOrder(request));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();

        assertEquals(OrderStatus.FAILED, saved.getStatus());
        assertEquals(2, saved.getStatusHistory().size());
        OrderStatusHistory last = saved.getStatusHistory().get(1);
        assertEquals(OrderStatus.PENDING, last.getPreviousStatus());
        assertEquals(OrderStatus.FAILED, last.getNewStatus());
        assertEquals("STOCK_INSUFFICIENT", last.getReason());
        verifyNoInteractions(orderMapper);
    }

    @Test
    @DisplayName("Si Inventory no reporta el producto, el pedido se guarda como FAILED")
    void createOrder_productMissingInInventoryResponse_isFailed() {
        CreateOrderRequest request = requestFor("prod-1", 1);
        when(inventoryClient.checkAvailability(any(StockCheckRequest.class)))
                .thenReturn(stockResponse(availableItem("otro-producto", 10, "Otro", "5.00")));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(StockInsufficientException.class, () -> orderService.createOrder(request));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertEquals(OrderStatus.FAILED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Si Inventory no está disponible, se propaga el error y no se guarda ningún pedido")
    void createOrder_inventoryDown_propagatesAndDoesNotSave() {
        CreateOrderRequest request = requestFor("prod-1", 1);
        when(inventoryClient.checkAvailability(any(StockCheckRequest.class)))
                .thenThrow(new InventoryUnavailableException("down", new RuntimeException("boom")));

        assertThrows(InventoryUnavailableException.class, () -> orderService.createOrder(request));

        verifyNoInteractions(orderRepository);
    }

    // ------------------------------------------------------------------
    // cancelOrder
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED"})
    @DisplayName("Se puede cancelar un pedido PENDING o CONFIRMED y se registra en el historial")
    void cancelOrder_fromCancellableStatus_cancelsAndRecordsHistory(OrderStatus initialStatus) {
        UUID orderId = UUID.randomUUID();
        Order order = orderWithStatus(orderId, initialStatus);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderMapper.toResponse(any(Order.class)))
                .thenReturn(OrderResponse.builder().status("CANCELLED").build());

        OrderResponse response = orderService.cancelOrder(orderId);

        assertEquals("CANCELLED", response.getStatus());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(2, order.getStatusHistory().size());
        OrderStatusHistory last = order.getStatusHistory().get(1);
        assertEquals(2, last.getSequenceNumber());
        assertEquals(initialStatus, last.getPreviousStatus());
        assertEquals(OrderStatus.CANCELLED, last.getNewStatus());
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("Cancelar un pedido ya CANCELLED lanza OrderAlreadyCancelledException")
    void cancelOrder_alreadyCancelled_throws() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(orderWithStatus(orderId, OrderStatus.CANCELLED)));

        assertThrows(OrderAlreadyCancelledException.class, () -> orderService.cancelOrder(orderId));

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Cancelar un pedido FAILED lanza InvalidStateTransitionException")
    void cancelOrder_failedOrder_throwsInvalidTransition() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(orderWithStatus(orderId, OrderStatus.FAILED)));

        assertThrows(InvalidStateTransitionException.class, () -> orderService.cancelOrder(orderId));

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Cancelar un pedido inexistente lanza OrderNotFoundException")
    void cancelOrder_notFound_throws() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> orderService.cancelOrder(orderId));

        verify(orderRepository, never()).save(any(Order.class));
    }

    // ------------------------------------------------------------------
    // getOrderById / getOrderHistory
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Consultar un pedido existente devuelve su respuesta mapeada")
    void getOrderById_found_returnsMappedResponse() {
        UUID orderId = UUID.randomUUID();
        Order order = orderWithStatus(orderId, OrderStatus.CONFIRMED);
        OrderResponse expected = OrderResponse.builder().id(orderId).status("CONFIRMED").build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(expected);

        OrderResponse response = orderService.getOrderById(orderId);

        assertEquals(orderId, response.getId());
        assertEquals("CONFIRMED", response.getStatus());
    }

    @Test
    @DisplayName("Consultar un pedido inexistente lanza OrderNotFoundException")
    void getOrderById_notFound_throws() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> orderService.getOrderById(orderId));
    }

    @Test
    @DisplayName("El historial se devuelve ordenado por secuencia")
    void getOrderHistory_returnsEntriesInOrder() {
        UUID orderId = UUID.randomUUID();
        OrderStatusHistory h1 = OrderStatusHistory.builder()
                .sequenceNumber(1).newStatus(OrderStatus.PENDING).build();
        OrderStatusHistory h2 = OrderStatusHistory.builder()
                .sequenceNumber(2).previousStatus(OrderStatus.PENDING).newStatus(OrderStatus.CONFIRMED).build();
        when(orderRepository.existsById(orderId)).thenReturn(true);
        when(orderStatusHistoryRepository.findByOrderIdOrderBySequenceNumberAsc(orderId))
                .thenReturn(List.of(h1, h2));
        when(orderMapper.toHistoryEntryResponse(h1))
                .thenReturn(OrderHistoryEntryResponse.builder().sequenceNumber(1).newStatus("PENDING").build());
        when(orderMapper.toHistoryEntryResponse(h2))
                .thenReturn(OrderHistoryEntryResponse.builder().sequenceNumber(2).newStatus("CONFIRMED").build());

        OrderHistoryResponse response = orderService.getOrderHistory(orderId);

        assertEquals(orderId, response.getOrderId());
        assertEquals(2, response.getHistory().size());
        assertEquals(1, response.getHistory().get(0).getSequenceNumber());
        assertEquals(2, response.getHistory().get(1).getSequenceNumber());
    }

    @Test
    @DisplayName("Historial de un pedido inexistente lanza OrderNotFoundException")
    void getOrderHistory_orderNotFound_throws() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.existsById(orderId)).thenReturn(false);

        assertThrows(OrderNotFoundException.class, () -> orderService.getOrderHistory(orderId));

        verifyNoInteractions(orderStatusHistoryRepository);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private CreateOrderRequest requestFor(String productId, int quantity) {
        return CreateOrderRequest.builder()
                .customerId("c-1")
                .items(List.of(CreateOrderItemRequest.builder()
                        .productId(productId)
                        .quantity(quantity)
                        .build()))
                .build();
    }

    private StockCheckResponse stockResponse(StockCheckItemResult... results) {
        return StockCheckResponse.builder().results(List.of(results)).build();
    }

    private StockCheckItemResult availableItem(String productId, int stock, String name, String price) {
        return StockCheckItemResult.builder()
                .productId(productId)
                .available(true)
                .availableStock(stock)
                .productName(name)
                .unitPrice(new BigDecimal(price))
                .build();
    }

    private StockCheckItemResult unavailableItem(String productId, int stock) {
        return StockCheckItemResult.builder()
                .productId(productId)
                .available(false)
                .availableStock(stock)
                .build();
    }

    private Order orderWithStatus(UUID id, OrderStatus status) {
        Order order = Order.builder()
                .id(id)
                .customerId("c-1")
                .status(status)
                .totalAmount(BigDecimal.TEN)
                .build();
        order.addStatusHistory(OrderStatusHistory.builder().newStatus(OrderStatus.PENDING).build());
        return order;
    }
}