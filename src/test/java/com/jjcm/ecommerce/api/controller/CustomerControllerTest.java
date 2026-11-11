package com.jjcm.ecommerce.api.controller;

import com.jjcm.ecommerce.api.dto.CustomerRequest;
import com.jjcm.ecommerce.api.dto.CustomerResponse;
import com.jjcm.ecommerce.api.exception.ResourceNotFoundException;
import com.jjcm.ecommerce.api.service.CustomerService;
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

@WebMvcTest(CustomerController.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CustomerControllerTest {

    private static final String VALID_BODY = """
            {"firstName": "Ana", "lastName": "Lopez", "email": "ana@example.com", "phone": "5551234"}
            """;

    private final MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    CustomerControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void should_return201_when_customer_is_valid() throws Exception {
        // Arrange
        when(customerService.create(any(CustomerRequest.class)))
                .thenReturn(CustomerResponse.builder().id(1L).firstName("Ana").email("ana@example.com").build());

        // Act
        var result = mockMvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY));

        // Assert
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void should_return400_when_email_is_invalid() throws Exception {
        // Arrange
        String body = """
                {"firstName": "Ana", "lastName": "Lopez", "email": "not-an-email"}
                """;

        // Act
        var result = mockMvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("email"));
    }

    @Test
    void should_return400_when_first_name_is_missing() throws Exception {
        // Arrange
        String body = """
                {"lastName": "Lopez", "email": "ana@example.com"}
                """;

        // Act
        var result = mockMvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("firstName"));
    }

    @Test
    void should_return200_when_customers_exist() throws Exception {
        // Arrange
        when(customerService.findAll()).thenReturn(List.of(
                CustomerResponse.builder().id(1L).firstName("Ana").build()));

        // Act
        var result = mockMvc.perform(get("/api/v1/customers"));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void should_return404_when_customer_does_not_exist() throws Exception {
        // Arrange
        when(customerService.findById(99L)).thenThrow(new ResourceNotFoundException("Customer", 99L));

        // Act
        var result = mockMvc.perform(get("/api/v1/customers/99"));

        // Assert
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void should_return200_when_update_is_valid() throws Exception {
        // Arrange
        when(customerService.update(anyLong(), any(CustomerRequest.class)))
                .thenReturn(CustomerResponse.builder().id(1L).firstName("Ana").build());

        // Act
        var result = mockMvc.perform(put("/api/v1/customers/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ana"));
    }

    @Test
    void should_return204_when_customer_is_deleted() throws Exception {
        // Arrange
        // (no stubbing needed: delete returns void)

        // Act
        var result = mockMvc.perform(delete("/api/v1/customers/1"));

        // Assert
        result.andExpect(status().isNoContent());
        verify(customerService).delete(1L);
    }
}
