package com.jjcm.ecommerce.api.controller;

import com.jjcm.ecommerce.api.dto.OrderCreateRequest;
import com.jjcm.ecommerce.api.dto.OrderResponse;
import com.jjcm.ecommerce.api.entity.OrderStatus;
import com.jjcm.ecommerce.api.entity.PaymentStatus;
import com.jjcm.ecommerce.api.exception.BusinessRuleException;
import com.jjcm.ecommerce.api.exception.ResourceNotFoundException;
import com.jjcm.ecommerce.api.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OrderControllerTest {

    private static final String CREATE_BODY = """
            {
              "customerId": 1,
              "shippingAddress": "12 Main St",
              "paymentMethod": "CREDIT_CARD",
              "items": [{"productId": 10, "quantity": 2}]
            }
            """;

    private final MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    OrderControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void should_return201_when_order_is_valid() throws Exception {
        // Arrange
        when(orderService.create(any(OrderCreateRequest.class)))
                .thenReturn(OrderResponse.builder().id(1L).status(OrderStatus.PENDING).build());

        // Act
        var result = mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY));

        // Assert
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void should_return400_when_items_list_is_empty() throws Exception {
        // Arrange
        String body = """
                {"customerId": 1, "shippingAddress": "12 Main St", "paymentMethod": "CASH_ON_DELIVERY", "items": []}
                """;

        // Act
        var result = mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("items"));
    }

    @Test
    void should_return422_when_stock_is_insufficient() throws Exception {
        // Arrange
        when(orderService.create(any(OrderCreateRequest.class)))
                .thenThrow(new BusinessRuleException("Insufficient stock for NB-001: requested 2, available 1"));

        // Act
        var result = mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY));

        // Assert
        result.andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Insufficient stock for NB-001: requested 2, available 1"));
    }

    @Test
    void should_return200_when_filtering_by_customer_and_status() throws Exception {
        // Arrange
        when(orderService.findAll(eq(1L), eq(OrderStatus.PENDING))).thenReturn(List.of(
                OrderResponse.builder().id(1L).status(OrderStatus.PENDING).build()));

        // Act
        var result = mockMvc.perform(get("/api/v1/orders")
                .param("customerId", "1")
                .param("status", "PENDING"));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void should_return400_when_status_filter_is_unknown() throws Exception {
        // Arrange
        // (no stubbing needed: the request fails to convert before reaching the service)

        // Act
        var result = mockMvc.perform(get("/api/v1/orders").param("status", "LOST"));

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: status"));
    }

    @Test
    void should_return404_when_order_does_not_exist() throws Exception {
        // Arrange
        when(orderService.findById(99L)).thenThrow(new ResourceNotFoundException("Order", 99L));

        // Act
        var result = mockMvc.perform(get("/api/v1/orders/99"));

        // Assert
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void should_return200_when_status_transition_is_allowed() throws Exception {
        // Arrange
        when(orderService.updateStatus(anyLong(), eq(OrderStatus.CONFIRMED)))
                .thenReturn(OrderResponse.builder().id(1L).status(OrderStatus.CONFIRMED).build());

        // Act
        var result = mockMvc.perform(put("/api/v1/orders/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"CONFIRMED\"}"));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void should_return422_when_status_transition_is_not_allowed() throws Exception {
        // Arrange
        when(orderService.updateStatus(anyLong(), eq(OrderStatus.DELIVERED)))
                .thenThrow(new BusinessRuleException("Cannot change order status from PENDING to DELIVERED"));

        // Act
        var result = mockMvc.perform(put("/api/v1/orders/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"DELIVERED\"}"));

        // Assert
        result.andExpect(status().isUnprocessableEntity());
    }

    @Test
    void should_return400_when_status_update_has_no_status() throws Exception {
        // Arrange
        // (no stubbing needed: validation fails before the service is called)

        // Act
        var result = mockMvc.perform(put("/api/v1/orders/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("status"));
    }

    @Test
    void should_return200_when_payment_status_is_updated() throws Exception {
        // Arrange
        when(orderService.updatePaymentStatus(anyLong(), eq(PaymentStatus.COMPLETED)))
                .thenReturn(OrderResponse.builder().id(1L).build());

        // Act
        var result = mockMvc.perform(put("/api/v1/orders/1/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentStatus\": \"COMPLETED\"}"));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
        verify(orderService).updatePaymentStatus(eq(1L), eq(PaymentStatus.COMPLETED));
    }
}
