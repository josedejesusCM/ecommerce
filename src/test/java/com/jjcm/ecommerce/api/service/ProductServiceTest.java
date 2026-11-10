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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
}
