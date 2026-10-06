package com.reto.inventory.service;

import com.reto.inventory.dto.request.CreateProductRequest;
import com.reto.inventory.dto.request.StockCheckRequest;
import com.reto.inventory.dto.response.ProductResponse;
import com.reto.inventory.dto.response.StockCheckResponse;

import java.util.List;
import java.util.UUID;

public interface InventoryService {

    ProductResponse getProductById(UUID productId);

    List<ProductResponse> getAllProducts();

    ProductResponse createProduct(CreateProductRequest request);

    StockCheckResponse checkAvailability(StockCheckRequest request);
}