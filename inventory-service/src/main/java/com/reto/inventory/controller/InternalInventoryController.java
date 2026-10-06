package com.reto.inventory.controller;

import com.reto.inventory.dto.request.StockCheckRequest;
import com.reto.inventory.dto.response.StockCheckResponse;
import com.reto.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/products")
@RequiredArgsConstructor
public class InternalInventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/check-availability")
    public ResponseEntity<StockCheckResponse> checkAvailability(@RequestBody StockCheckRequest request) {
        StockCheckResponse response = inventoryService.checkAvailability(request);
        return ResponseEntity.ok(response);
    }
}