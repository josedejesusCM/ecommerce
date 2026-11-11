package com.jjcm.ecommerce.api.controller;

import com.jjcm.ecommerce.api.dto.ProductRequest;
import com.jjcm.ecommerce.api.dto.ProductResponse;
import com.jjcm.ecommerce.api.exception.BusinessRuleException;
import com.jjcm.ecommerce.api.exception.ResourceNotFoundException;
import com.jjcm.ecommerce.api.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ProductControllerTest {

    private static final String VALID_BODY = """
            {"name": "Notebook", "price": 12.50, "sku": "NB-001", "stockQuantity": 5, "categoryId": 1}
            """;

    private final MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    ProductControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void should_return201_when_product_is_valid() throws Exception {
        // Arrange
        when(productService.create(any(ProductRequest.class)))
                .thenReturn(ProductResponse.builder().id(1L).name("Notebook").sku("NB-001").build());

        // Act
        var result = mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY));

        // Assert
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("NB-001"));
    }

    @Test
    void should_return400_when_price_is_missing() throws Exception {
        // Arrange
        String body = """
                {"name": "Notebook", "sku": "NB-001", "categoryId": 1}
                """;

        // Act
        var result = mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("price"));
    }

    @Test
    void should_return400_when_stock_quantity_is_negative() throws Exception {
        // Arrange
        String body = """
                {"name": "Notebook", "price": 12.50, "sku": "NB-001", "stockQuantity": -1, "categoryId": 1}
                """;

        // Act
        var result = mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("stockQuantity"));
    }

    @Test
    void should_return200_when_products_exist() throws Exception {
        // Arrange
        when(productService.findAll()).thenReturn(List.of(
                ProductResponse.builder().id(1L).name("Notebook").build()));

        // Act
        var result = mockMvc.perform(get("/api/v1/products"));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void should_return404_when_product_does_not_exist() throws Exception {
        // Arrange
        when(productService.findById(99L)).thenThrow(new ResourceNotFoundException("Product", 99L));

        // Act
        var result = mockMvc.perform(get("/api/v1/products/99"));

        // Assert
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void should_return422_when_service_rejects_the_change() throws Exception {
        // Arrange
        when(productService.update(anyLong(), any(ProductRequest.class)))
                .thenThrow(new BusinessRuleException("SKU already in use"));

        // Act
        var result = mockMvc.perform(put("/api/v1/products/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY));

        // Assert
        result.andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("SKU already in use"));
    }

    @Test
    void should_return204_when_product_is_deleted() throws Exception {
        // Arrange
        // (no stubbing needed: delete returns void)

        // Act
        var result = mockMvc.perform(delete("/api/v1/products/1"));

        // Assert
        result.andExpect(status().isNoContent());
        verify(productService).delete(1L);
    }
}
