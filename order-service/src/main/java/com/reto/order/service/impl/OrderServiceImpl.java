package com.reto.order.service.impl;

import com.reto.order.client.InventoryClient;
import com.reto.order.client.dto.StockCheckItemRequest;
import com.reto.order.client.dto.StockCheckItemResult;
import com.reto.order.client.dto.StockCheckRequest;
import com.reto.order.client.dto.StockCheckResponse;
import com.reto.order.domain.model.Order;
import com.reto.order.domain.model.OrderItem;
import com.reto.order.domain.model.OrderStatus;
import com.reto.order.domain.model.OrderStatusHistory;
import com.reto.order.domain.model.OrderStatusHistory;
import com.reto.order.domain.repository.OrderRepository;
import com.reto.order.domain.repository.OrderStatusHistoryRepository;
import com.reto.order.domain.statemachine.OrderStateMachine;
import com.reto.order.dto.request.CreateOrderItemRequest;
import com.reto.order.dto.request.CreateOrderRequest;
import com.reto.order.dto.response.OrderHistoryResponse;
import com.reto.order.dto.response.OrderResponse;
import com.reto.order.exception.OrderNotFoundException;
import com.reto.order.exception.StockInsufficientException;
import com.reto.order.mapper.OrderMapper;
import com.reto.order.service.OrderService;
import com.reto.order.util.TraceIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String STOCK_INSUFFICIENT_REASON = "STOCK_INSUFFICIENT";

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final InventoryClient inventoryClient;
    private final OrderStateMachine orderStateMachine;
    private final OrderMapper orderMapper;

    @Override
    @Transactional(noRollbackFor = StockInsufficientException.class)
    public OrderResponse createOrder(CreateOrderRequest request) {
        StockCheckResponse stockCheckResponse = checkStockAvailability(request);
        Map<String, StockCheckItemResult> resultsByProductId = stockCheckResponse.getResults().stream()
                .collect(Collectors.toMap(StockCheckItemResult::getProductId, Function.identity()));

        boolean allAvailable = request.getItems().stream()
                .allMatch(item -> {
                    StockCheckItemResult result = resultsByProductId.get(item.getProductId());
                    return result != null && result.isAvailable();
                });

        Order order = buildOrderWithItems(request, resultsByProductId);
        order.addStatusHistory(buildHistoryEntry(null, OrderStatus.PENDING, null));

        OrderStatus targetStatus = allAvailable ? OrderStatus.CONFIRMED : OrderStatus.FAILED;
        String reason = allAvailable ? null : STOCK_INSUFFICIENT_REASON;

        orderStateMachine.validateTransition(OrderStatus.PENDING, targetStatus);
        order.setStatus(targetStatus);
        order.addStatusHistory(buildHistoryEntry(OrderStatus.PENDING, targetStatus, reason));

        Order savedOrder = orderRepository.save(order);

        if (!allAvailable) {
            throw new StockInsufficientException("Stock insuficiente para uno o más productos del pedido.");
        }

        return orderMapper.toResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        List<Order> orders = orderRepository.findAll();
        return orderMapper.toResponseList(orders);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderHistoryResponse getOrderHistory(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new OrderNotFoundException(orderId);
        }

        List<OrderStatusHistory> historyEntries =
                orderStatusHistoryRepository.findByOrderIdOrderBySequenceNumberAsc(orderId);

        return OrderHistoryResponse.builder()
                .orderId(orderId)
                .history(historyEntries.stream()
                        .map(orderMapper::toHistoryEntryResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        OrderStatus previousStatus = order.getStatus();
        orderStateMachine.validateCancellation(orderId, previousStatus);

        order.setStatus(OrderStatus.CANCELLED);
        order.addStatusHistory(buildHistoryEntry(previousStatus, OrderStatus.CANCELLED, null));

        Order savedOrder = orderRepository.save(order);
        return orderMapper.toResponse(savedOrder);
    }

    private StockCheckResponse checkStockAvailability(CreateOrderRequest request) {
        List<StockCheckItemRequest> stockItems = request.getItems().stream()
                .map(item -> StockCheckItemRequest.builder()
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .build())
                .collect(Collectors.toList());

        return inventoryClient.checkAvailability(
                StockCheckRequest.builder().items(stockItems).build()
        );
    }

    private Order buildOrderWithItems(CreateOrderRequest request, Map<String, StockCheckItemResult> resultsByProductId) {
        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CreateOrderItemRequest itemRequest : request.getItems()) {
            StockCheckItemResult result = resultsByProductId.get(itemRequest.getProductId());

            BigDecimal unitPrice = (result != null && result.getUnitPrice() != null)
                    ? result.getUnitPrice()
                    : BigDecimal.ZERO;

            String productName = result != null ? result.getProductName() : null;

            OrderItem orderItem = OrderItem.builder()
                    .productId(itemRequest.getProductId())
                    .productName(productName)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(unitPrice)
                    .build();

            order.addItem(orderItem);
            totalAmount = totalAmount.add(unitPrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity())));
        }

        order.setTotalAmount(totalAmount);
        return order;
    }

    private OrderStatusHistory buildHistoryEntry(OrderStatus previousStatus, OrderStatus newStatus, String reason) {
        return OrderStatusHistory.builder()
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .reason(reason)
                .traceId(MDC.get(TraceIdFilter.MDC_KEY))
                .build();
    }
}