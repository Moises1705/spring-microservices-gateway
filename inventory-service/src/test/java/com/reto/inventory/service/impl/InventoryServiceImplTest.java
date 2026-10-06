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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    private InventoryServiceImpl inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryServiceImpl(productRepository, productMapper);
    }

    // ------------------------------------------------------------------
    // Consulta de productos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Obtener un producto existente devuelve su respuesta mapeada")
    void getProductById_found_returnsMappedResponse() {
        UUID id = UUID.randomUUID();
        Product product = product(id, 10, 0);
        ProductResponse expected = ProductResponse.builder().id(id).sku("SKU-1").build();
        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(productMapper.toResponse(product)).thenReturn(expected);

        ProductResponse response = inventoryService.getProductById(id);

        assertSame(expected, response);
    }

    @Test
    @DisplayName("Obtener un producto inexistente lanza ProductNotFoundException")
    void getProductById_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> inventoryService.getProductById(id));

        verifyNoInteractions(productMapper);
    }

    @Test
    @DisplayName("Listar productos delega en el repositorio y el mapper")
    void getAllProducts_returnsMappedList() {
        List<Product> products = List.of(product(UUID.randomUUID(), 5, 0), product(UUID.randomUUID(), 7, 1));
        List<ProductResponse> expected = List.of(ProductResponse.builder().build(), ProductResponse.builder().build());
        when(productRepository.findAll()).thenReturn(products);
        when(productMapper.toResponseList(products)).thenReturn(expected);

        List<ProductResponse> response = inventoryService.getAllProducts();

        assertEquals(2, response.size());
        assertSame(expected, response);
    }

    // ------------------------------------------------------------------
    // Creación de productos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Crear un producto con SKU nuevo lo guarda y devuelve la respuesta")
    void createProduct_newSku_savesAndReturnsResponse() {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("SKU-NEW").name("Mouse").stockAvailable(100).price(new BigDecimal("49.90")).build();
        Product entity = product(null, 100, 0);
        Product saved = product(UUID.randomUUID(), 100, 0);
        ProductResponse expected = ProductResponse.builder().id(saved.getId()).sku("SKU-NEW").build();
        when(productRepository.existsBySku("SKU-NEW")).thenReturn(false);
        when(productMapper.toEntity(request)).thenReturn(entity);
        when(productRepository.save(entity)).thenReturn(saved);
        when(productMapper.toResponse(saved)).thenReturn(expected);

        ProductResponse response = inventoryService.createProduct(request);

        assertSame(expected, response);
        verify(productRepository).save(entity);
    }

    @Test
    @DisplayName("Crear un producto con SKU duplicado lanza DuplicateSkuException y no guarda")
    void createProduct_duplicateSku_throws() {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("SKU-DUP").name("Mouse").stockAvailable(10).price(new BigDecimal("10.00")).build();
        when(productRepository.existsBySku("SKU-DUP")).thenReturn(true);

        assertThrows(DuplicateSkuException.class, () -> inventoryService.createProduct(request));

        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(productMapper);
    }

    // ------------------------------------------------------------------
    // Verificación de disponibilidad
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "cantidad pedida {0} con 8 realmente disponibles -> disponible = {1}")
    @CsvSource({"1,true", "8,true", "9,false"})
    @DisplayName("La disponibilidad real es stockAvailable - stockReserved")
    void checkAvailability_usesAvailableMinusReserved(int requested, boolean expectedAvailable) {
        UUID id = UUID.randomUUID();
        // 10 disponibles - 2 reservados = 8 reales
        when(productRepository.findById(id)).thenReturn(Optional.of(product(id, 10, 2)));

        StockCheckResponse response = inventoryService.checkAvailability(request(item(id.toString(), requested)));

        StockCheckItemResult result = response.getResults().get(0);
        assertEquals(expectedAvailable, result.isAvailable());
        assertEquals(8, result.getAvailableStock());
        assertEquals("Mouse", result.getProductName());
        assertEquals(0, new BigDecimal("15.50").compareTo(result.getUnitPrice()));
    }

    @Test
    @DisplayName("Un producto inexistente se reporta como no disponible con stock 0")
    void checkAvailability_unknownProduct_isUnavailable() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        StockCheckResponse response = inventoryService.checkAvailability(request(item(id.toString(), 1)));

        StockCheckItemResult result = response.getResults().get(0);
        assertFalse(result.isAvailable());
        assertEquals(0, result.getAvailableStock());
        assertEquals(id.toString(), result.getProductId());
    }

    @Test
    @DisplayName("Un productId que no es UUID se reporta como no disponible sin consultar la base de datos")
    void checkAvailability_invalidUuid_isUnavailableWithoutQueryingRepository() {
        StockCheckResponse response = inventoryService.checkAvailability(request(item("no-es-uuid", 1)));

        StockCheckItemResult result = response.getResults().get(0);
        assertFalse(result.isAvailable());
        assertEquals(0, result.getAvailableStock());
        assertEquals("no-es-uuid", result.getProductId());
        verifyNoInteractions(productRepository);
    }

    @Test
    @DisplayName("Con varios items se devuelve un resultado por item, en el mismo orden")
    void checkAvailability_multipleItems_keepsOrder() {
        UUID okId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        when(productRepository.findById(okId)).thenReturn(Optional.of(product(okId, 10, 0)));
        when(productRepository.findById(missingId)).thenReturn(Optional.empty());

        StockCheckResponse response = inventoryService.checkAvailability(
                request(item(okId.toString(), 3), item(missingId.toString(), 1)));

        assertEquals(2, response.getResults().size());
        assertEquals(okId.toString(), response.getResults().get(0).getProductId());
        assertTrue(response.getResults().get(0).isAvailable());
        assertEquals(missingId.toString(), response.getResults().get(1).getProductId());
        assertFalse(response.getResults().get(1).isAvailable());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Product product(UUID id, int stockAvailable, int stockReserved) {
        return Product.builder()
                .id(id)
                .sku("SKU-1")
                .name("Mouse")
                .stockAvailable(stockAvailable)
                .stockReserved(stockReserved)
                .price(new BigDecimal("15.50"))
                .build();
    }

    private StockCheckItemRequest item(String productId, int quantity) {
        return StockCheckItemRequest.builder().productId(productId).quantity(quantity).build();
    }

    private StockCheckRequest request(StockCheckItemRequest... items) {
        return StockCheckRequest.builder().items(List.of(items)).build();
    }
}