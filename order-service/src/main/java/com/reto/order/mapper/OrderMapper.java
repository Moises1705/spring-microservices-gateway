package com.reto.order.mapper;

import com.reto.order.domain.model.Order;
import com.reto.order.domain.model.OrderItem;
import com.reto.order.domain.model.OrderStatusHistory;
import com.reto.order.dto.response.OrderHistoryEntryResponse;
import com.reto.order.dto.response.OrderItemResponse;
import com.reto.order.dto.response.OrderResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderResponse toResponse(Order order);

    List<OrderResponse> toResponseList(List<Order> orders);

    OrderItemResponse toItemResponse(OrderItem item);

    OrderHistoryEntryResponse toHistoryEntryResponse(OrderStatusHistory history);
}