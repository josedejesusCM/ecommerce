package com.jjcm.ecommerce.api.controller;

import com.jjcm.ecommerce.api.dto.ProductResponse;
import com.jjcm.ecommerce.api.exception.BusinessRuleException;
import com.jjcm.ecommerce.api.exception.DuplicateResourceException;
import com.jjcm.ecommerce.api.exception.ResourceNotFoundException;
import com.jjcm.ecommerce.api.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    private static final String VALID_BODY = """
            {
              "name": "Notebook",
              "description": "A4 ruled",
              "price": 4.50,
              "sku": "NB-A4-01",
              "stockQuantity": 20,
              "active": true,
              "categoryId": 1
            }
            """;

    private ProductResponse sampleProduct() {
        return ProductResponse.builder()
                .id(1L)
                .name("Notebook")
                .description("A4 ruled")
                .price(new BigDecimal("4.50"))
                .sku("NB-A4-01")
                .stockQuantity(20)
                .active(true)
                .categoryId(1L)
                .categoryName("Stationery")
                .build();
    }

    // POST /api/v1/products

    @Test
    void should_return201_when_createRequestIsValid() throws Exception {
        // Arrange
        when(productService.create(any())).thenReturn(sampleProduct());

        // Act
        var request = post("/api/v1/products").contentType(APPLICATION_JSON).content(VALID_BODY);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("NB-A4-01"));
    }

    @Test
    void should_return400_listingAllFieldErrors_when_createRequestIsInvalid() throws Exception {
        // Arrange
        String body = """
                {
                  "name": "",
                  "sku": ""
                }
                """;

        // Act
        var request = post("/api/v1/products").contentType(APPLICATION_JSON).content(body);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.length()").value(4));
    }

    @Test
    void should_return409_when_skuAlreadyExists() throws Exception {
        // Arrange
        when(productService.create(any()))
                .thenThrow(new DuplicateResourceException("Product SKU already exists: NB-A4-01"));

        // Act
        var request = post("/api/v1/products").contentType(APPLICATION_JSON).content(VALID_BODY);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isConflict());
    }

    @Test
    void should_return404_when_categoryDoesNotExist() throws Exception {
        // Arrange
        when(productService.create(any())).thenThrow(new ResourceNotFoundException("Category", 1L));

        // Act
        var request = post("/api/v1/products").contentType(APPLICATION_JSON).content(VALID_BODY);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isNotFound());
    }

    // GET /api/v1/products

    @Test
    void should_return200_withAllProducts_when_listRequested() throws Exception {
        // Arrange
        when(productService.findAll()).thenReturn(List.of(sampleProduct()));

        // Act
        var request = get("/api/v1/products");

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // GET /api/v1/products/{id}

    @Test
    void should_return200_when_productExists() throws Exception {
        // Arrange
        when(productService.findById(1L)).thenReturn(sampleProduct());

        // Act
        var request = get("/api/v1/products/{id}", 1L);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("Stationery"));
    }

    @Test
    void should_return404_when_productDoesNotExist() throws Exception {
        // Arrange
        when(productService.findById(99L)).thenThrow(new ResourceNotFoundException("Product", 99L));

        // Act
        var request = get("/api/v1/products/{id}", 99L);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isNotFound());
    }

    // PUT /api/v1/products/{id}

    @Test
    void should_return200_when_updateRequestIsValid() throws Exception {
        // Arrange
        when(productService.update(anyLong(), any())).thenReturn(sampleProduct());

        // Act
        var request = put("/api/v1/products/{id}", 1L).contentType(APPLICATION_JSON).content(VALID_BODY);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // DELETE /api/v1/products/{id}

    @Test
    void should_return204_when_productDeleted() throws Exception {
        // Arrange
        // service.delete is a void no-op by default on a mock

        // Act
        var request = delete("/api/v1/products/{id}", 1L);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isNoContent());
    }

    @Test
    void should_return422_when_productAppearsInOrders() throws Exception {
        // Arrange
        doThrow(new BusinessRuleException("Product 1 appears in orders and cannot be deleted"))
                .when(productService).delete(1L);

        // Act
        var request = delete("/api/v1/products/{id}", 1L);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isUnprocessableEntity());
    }
}
