package com.jjcm.ecommerce.api.controller;

import com.jjcm.ecommerce.api.dto.OrderResponse;
import com.jjcm.ecommerce.api.entity.OrderStatus;
import com.jjcm.ecommerce.api.exception.BusinessRuleException;
import com.jjcm.ecommerce.api.exception.ResourceNotFoundException;
import com.jjcm.ecommerce.api.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private static final String VALID_CREATE_BODY = """
            {
              "customerId": 1,
              "shippingAddress": "1 Main St",
              "paymentMethod": "CREDIT_CARD",
              "items": [{ "productId": 10, "quantity": 2 }]
            }
            """;

    private OrderResponse sampleOrder(OrderStatus status) {
        return OrderResponse.builder()
                .id(1L)
                .orderNumber("ORD-0001")
                .customerId(1L)
                .status(status)
                .totalAmount(new BigDecimal("50.00"))
                .shippingAddress("1 Main St")
                .items(List.of())
                .build();
    }

    // POST /api/v1/orders

    @Test
    void should_return201_when_createRequestIsValid() throws Exception {
        // Arrange
        when(orderService.create(any())).thenReturn(sampleOrder(OrderStatus.PENDING));

        // Act
        var request = post("/api/v1/orders").contentType(APPLICATION_JSON).content(VALID_CREATE_BODY);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void should_return400_listingAllFieldErrors_when_createRequestHasMissingFields() throws Exception {
        // Arrange
        String emptyBody = "{}";

        // Act
        var request = post("/api/v1/orders").contentType(APPLICATION_JSON).content(emptyBody);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.length()").value(4));
    }

    @Test
    void should_return400_when_createRequestHasEmptyItems() throws Exception {
        // Arrange
        String body = """
                {
                  "customerId": 1,
                  "shippingAddress": "1 Main St",
                  "paymentMethod": "CREDIT_CARD",
                  "items": []
                }
                """;

        // Act
        var request = post("/api/v1/orders").contentType(APPLICATION_JSON).content(body);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("items"));
    }

    @Test
    void should_return404_when_createReferencesUnknownCustomer() throws Exception {
        // Arrange
        when(orderService.create(any())).thenThrow(new ResourceNotFoundException("Customer", 1L));

        // Act
        var request = post("/api/v1/orders").contentType(APPLICATION_JSON).content(VALID_CREATE_BODY);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return422_when_createViolatesBusinessRule() throws Exception {
        // Arrange
        when(orderService.create(any()))
                .thenThrow(new BusinessRuleException("Insufficient stock for SKU-1: requested 2, available 0"));

        // Act
        var request = post("/api/v1/orders").contentType(APPLICATION_JSON).content(VALID_CREATE_BODY);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isUnprocessableEntity());
    }

    // GET /api/v1/orders

    @Test
    void should_return200_withAllOrders_when_noFiltersGiven() throws Exception {
        // Arrange
        when(orderService.findAll(null, null)).thenReturn(List.of(sampleOrder(OrderStatus.PENDING)));

        // Act
        var request = get("/api/v1/orders");

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void should_passCustomerIdAndStatusToService_when_filtersGiven() throws Exception {
        // Arrange
        when(orderService.findAll(1L, OrderStatus.PENDING)).thenReturn(List.of());

        // Act
        mockMvc.perform(get("/api/v1/orders")
                .param("customerId", "1")
                .param("status", "PENDING"));

        // Assert
        verify(orderService).findAll(1L, OrderStatus.PENDING);
    }

    // GET /api/v1/orders/{id}

    @Test
    void should_return200_when_orderExists() throws Exception {
        // Arrange
        when(orderService.findById(1L)).thenReturn(sampleOrder(OrderStatus.PENDING));

        // Act
        var request = get("/api/v1/orders/{id}", 1L);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("ORD-0001"));
    }

    @Test
    void should_return404_when_orderDoesNotExist() throws Exception {
        // Arrange
        when(orderService.findById(99L)).thenThrow(new ResourceNotFoundException("Order", 99L));

        // Act
        var request = get("/api/v1/orders/{id}", 99L);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isNotFound());
    }

    // PUT /api/v1/orders/{id}/status

    @Test
    void should_return200_when_statusTransitionIsAllowed() throws Exception {
        // Arrange
        when(orderService.updateStatus(anyLong(), any())).thenReturn(sampleOrder(OrderStatus.CONFIRMED));
        String body = """
                { "status": "CONFIRMED" }
                """;

        // Act
        var request = put("/api/v1/orders/{id}/status", 1L).contentType(APPLICATION_JSON).content(body);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void should_return422_when_statusTransitionIsNotAllowed() throws Exception {
        // Arrange
        when(orderService.updateStatus(anyLong(), any()))
                .thenThrow(new BusinessRuleException("Cannot change order status from DELIVERED to PENDING"));
        String body = """
                { "status": "PENDING" }
                """;

        // Act
        var request = put("/api/v1/orders/{id}/status", 1L).contentType(APPLICATION_JSON).content(body);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void should_return400_when_statusIsMissing() throws Exception {
        // Arrange
        String body = "{}";

        // Act
        var request = put("/api/v1/orders/{id}/status", 1L).contentType(APPLICATION_JSON).content(body);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("status"));
    }

    // PUT /api/v1/orders/{id}/payment

    @Test
    void should_return200_when_paymentStatusIsUpdated() throws Exception {
        // Arrange
        OrderResponse updated = sampleOrder(OrderStatus.CONFIRMED);
        when(orderService.updatePaymentStatus(anyLong(), any())).thenReturn(updated);
        String body = """
                { "paymentStatus": "COMPLETED" }
                """;

        // Act
        var request = put("/api/v1/orders/{id}/payment", 1L).contentType(APPLICATION_JSON).content(body);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void should_return422_when_orderIsCancelled() throws Exception {
        // Arrange
        when(orderService.updatePaymentStatus(anyLong(), any()))
                .thenThrow(new BusinessRuleException("Cannot change the payment of a cancelled order"));
        String body = """
                { "paymentStatus": "COMPLETED" }
                """;

        // Act
        var request = put("/api/v1/orders/{id}/payment", 1L).contentType(APPLICATION_JSON).content(body);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void should_return400_when_paymentStatusIsMissing() throws Exception {
        // Arrange
        String body = "{}";

        // Act
        var request = put("/api/v1/orders/{id}/payment", 1L).contentType(APPLICATION_JSON).content(body);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("paymentStatus"));
    }
}
