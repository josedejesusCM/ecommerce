package com.jjcm.ecommerce.api.service;

import com.jjcm.ecommerce.api.dto.ProductRequest;
import com.jjcm.ecommerce.api.dto.ProductResponse;
import com.jjcm.ecommerce.api.entity.Category;
import com.jjcm.ecommerce.api.entity.Product;
import com.jjcm.ecommerce.api.exception.BusinessRuleException;
import com.jjcm.ecommerce.api.exception.DuplicateResourceException;
import com.jjcm.ecommerce.api.exception.ResourceNotFoundException;
import com.jjcm.ecommerce.api.repository.CategoryRepository;
import com.jjcm.ecommerce.api.repository.OrderItemRepository;
import com.jjcm.ecommerce.api.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private ProductService productService;

    private ProductRequest request() {
        return ProductRequest.builder()
                .name("Wireless Mouse")
                .description("Ergonomic 2.4GHz wireless mouse")
                .price(new BigDecimal("24.99"))
                .sku("ELEC-MOU-001")
                .categoryId(1L)
                .build();
    }

    @Test
    void should_throwDuplicateResourceException_when_skuAlreadyExists() {
        // Arrange
        Product existing = Product.builder().id(2L).sku("ELEC-MOU-001").build();
        when(productRepository.findBySku("ELEC-MOU-001")).thenReturn(Optional.of(existing));

        // Act
        Executable action = () -> productService.create(request());

        // Assert
        assertThrows(DuplicateResourceException.class, action);
    }

    @Test
    void should_returnProductWithZeroStockAndActive_when_createdWithoutOptionalFields() {
        // Arrange
        Category category = Category.builder().id(1L).name("Electronics").slug("electronics").build();
        when(productRepository.findBySku("ELEC-MOU-001")).thenReturn(Optional.empty());
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        // Act
        ProductResponse result = productService.create(request());

        // Assert
        assertEquals(0, result.getStockQuantity().intValue());
    }

    @Test
    void should_throwResourceNotFoundException_when_categoryDoesNotExist() {
        // Arrange
        when(productRepository.findBySku("ELEC-MOU-001")).thenReturn(Optional.empty());
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        // Act
        Executable action = () -> productService.create(request());

        // Assert
        assertThrows(ResourceNotFoundException.class, action);
    }

    @Test
    void should_throwBusinessRuleException_when_deletingProductThatAppearsInOrders() {
        // Arrange
        Product product = Product.builder().id(1L).sku("ELEC-MOU-001").build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderItemRepository.existsByProductId(1L)).thenReturn(true);

        // Act
        Executable action = () -> productService.delete(1L);

        // Assert
        assertThrows(BusinessRuleException.class, action);
    }

    private Category electronics() {
        return Category.builder().id(1L).name("Electronics").slug("electronics").build();
    }

    private Product existingProduct(long id, String sku, int stock, boolean active) {
        return Product.builder()
                .id(id)
                .name("Old Name")
                .price(new BigDecimal("10.00"))
                .sku(sku)
                .stockQuantity(stock)
                .active(active)
                .category(electronics())
                .build();
    }

    @Test
    void should_returnAllProducts_when_listRequested() {
        // Arrange
        when(productRepository.findAll()).thenReturn(List.of(existingProduct(1L, "ELEC-MOU-001", 5, true)));

        // Act
        List<ProductResponse> result = productService.findAll();

        // Assert
        assertEquals(1, result.size());
    }

    @Test
    void should_returnProduct_when_productExists() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct(1L, "ELEC-MOU-001", 5, true)));

        // Act
        ProductResponse result = productService.findById(1L);

        // Assert
        assertEquals("ELEC-MOU-001", result.getSku());
    }

    @Test
    void should_keepProvidedStockAndActiveFlag_when_createdWithOptionalFields() {
        // Arrange
        ProductRequest request = ProductRequest.builder()
                .name("Wireless Mouse")
                .price(new BigDecimal("24.99"))
                .sku("ELEC-MOU-001")
                .categoryId(1L)
                .stockQuantity(5)
                .active(false)
                .build();
        when(productRepository.findBySku("ELEC-MOU-001")).thenReturn(Optional.empty());
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics()));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ProductResponse result = productService.create(request);

        // Assert
        assertEquals(5, result.getStockQuantity().intValue());
    }

    @Test
    void should_updateStockAndActiveFlag_when_provided() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct(1L, "ELEC-MOU-001", 5, true)));
        when(productRepository.findBySku("ELEC-MOU-001")).thenReturn(Optional.empty());
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics()));
        ProductRequest request = ProductRequest.builder()
                .name("Wireless Mouse")
                .price(new BigDecimal("24.99"))
                .sku("ELEC-MOU-001")
                .categoryId(1L)
                .stockQuantity(15)
                .active(false)
                .build();

        // Act
        ProductResponse result = productService.update(1L, request);

        // Assert
        assertEquals(15, result.getStockQuantity().intValue());
    }

    @Test
    void should_keepStockAndActiveFlag_when_updateOmitsThem() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct(1L, "ELEC-MOU-001", 5, true)));
        when(productRepository.findBySku("ELEC-MOU-001")).thenReturn(Optional.empty());
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics()));

        // Act
        ProductResponse result = productService.update(1L, request());

        // Assert
        assertEquals(5, result.getStockQuantity().intValue());
    }

    @Test
    void should_throwDuplicateResourceException_when_updatedSkuBelongsToAnotherProduct() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct(1L, "OLD-SKU", 5, true)));
        when(productRepository.findBySku("ELEC-MOU-001"))
                .thenReturn(Optional.of(existingProduct(2L, "ELEC-MOU-001", 1, true)));

        // Act
        Executable action = () -> productService.update(1L, request());

        // Assert
        assertThrows(DuplicateResourceException.class, action);
    }

    @Test
    void should_notThrow_when_updatingProductWithItsOwnSku() {
        // Arrange
        Product product = existingProduct(1L, "ELEC-MOU-001", 5, true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.findBySku("ELEC-MOU-001")).thenReturn(Optional.of(product));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics()));

        // Act
        Executable action = () -> productService.update(1L, request());

        // Assert
        assertDoesNotThrow(action);
    }

    @Test
    void should_throwResourceNotFoundException_when_updatingUnknownProduct() {
        // Arrange
        when(productRepository.findById(9L)).thenReturn(Optional.empty());

        // Act
        Executable action = () -> productService.update(9L, request());

        // Assert
        assertThrows(ResourceNotFoundException.class, action);
    }

    @Test
    void should_deleteProduct_when_notInAnyOrder() {
        // Arrange
        Product product = existingProduct(1L, "ELEC-MOU-001", 5, true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderItemRepository.existsByProductId(1L)).thenReturn(false);

        // Act
        productService.delete(1L);

        // Assert
        verify(productRepository).delete(product);
    }
}
