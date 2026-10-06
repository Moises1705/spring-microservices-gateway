package com.reto.inventory.service.impl;

import com.reto.inventory.domain.model.Product;
import com.reto.inventory.domain.repository.ProductRepository;
import com.reto.inventory.dto.request.CreateProductRequest;
import com.reto.inventory.dto.request.StockCheckItemRequest;
import com.reto.inventory.dto.request.StockCheckRequest;
import com.reto.inventory.dto.response.ProductResponse;
import com.reto.inventory.dto.response.StockCheckItemResult;
import com.reto.inventory.dto.response.StockCheckResponse;
import com.reto.inventory.exception.DuplicateSkuException;
import com.reto.inventory.exception.ProductNotFoundException;
import com.reto.inventory.mapper.ProductMapper;
import com.reto.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    public ProductResponse getProductById(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        return productMapper.toResponse(product);
    }

    @Override
    public List<ProductResponse> getAllProducts() {
        List<Product> products = productRepository.findAll();
        return productMapper.toResponseList(products);
    }

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }

        Product product = productMapper.toEntity(request);
        Product savedProduct = productRepository.save(product);

        return productMapper.toResponse(savedProduct);
    }

    @Override
    public StockCheckResponse checkAvailability(StockCheckRequest request) {
        List<StockCheckItemResult> results = request.getItems().stream()
                .map(this::resolveAvailability)
                .collect(Collectors.toList());

        return StockCheckResponse.builder()
                .results(results)
                .build();
    }

    private StockCheckItemResult resolveAvailability(StockCheckItemRequest item) {
        UUID productId;
        try {
            productId = UUID.fromString(item.getProductId());
        } catch (IllegalArgumentException ex) {
            return StockCheckItemResult.builder()
                    .productId(item.getProductId())
                    .available(false)
                    .availableStock(0)
                    .build();
        }

        Optional<Product> productOpt = productRepository.findById(productId);

        if (productOpt.isEmpty()) {
            return StockCheckItemResult.builder()
                    .productId(item.getProductId())
                    .available(false)
                    .availableStock(0)
                    .build();
        }

        Product product = productOpt.get();
        int realAvailable = product.getStockAvailable() - product.getStockReserved();
        boolean isAvailable = realAvailable >= item.getQuantity();

        return StockCheckItemResult.builder()
                .productId(item.getProductId())
                .available(isAvailable)
                .availableStock(realAvailable)
                .productName(product.getName())
                .unitPrice(product.getPrice())
                .build();
    }
}