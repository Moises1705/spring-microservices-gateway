package com.reto.order.service;

import com.reto.order.dto.request.CreateOrderRequest;
import com.reto.order.dto.response.OrderHistoryResponse;
import com.reto.order.dto.response.OrderResponse;

import java.util.List;
import java.util.UUID;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request);

    OrderResponse getOrderById(UUID orderId);

    List<OrderResponse> getAllOrders();

    OrderHistoryResponse getOrderHistory(UUID orderId);

    OrderResponse cancelOrder(UUID orderId);
}