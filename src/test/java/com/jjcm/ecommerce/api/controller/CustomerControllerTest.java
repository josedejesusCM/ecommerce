package com.jjcm.ecommerce.api.controller;

import com.jjcm.ecommerce.api.dto.CustomerResponse;
import com.jjcm.ecommerce.api.exception.BusinessRuleException;
import com.jjcm.ecommerce.api.exception.DuplicateResourceException;
import com.jjcm.ecommerce.api.exception.ResourceNotFoundException;
import com.jjcm.ecommerce.api.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    private static final String VALID_BODY = """
            {
              "firstName": "Ada",
              "lastName": "Lovelace",
              "email": "ada@example.com",
              "phone": "+1555000111",
              "address": "1 Analytical Way"
            }
            """;

    private CustomerResponse sampleCustomer() {
        return CustomerResponse.builder()
                .id(1L)
                .firstName("Ada")
                .lastName("Lovelace")
                .email("ada@example.com")
                .phone("+1555000111")
                .address("1 Analytical Way")
                .build();
    }

    // POST /api/v1/customers

    @Test
    void should_return201_when_createRequestIsValid() throws Exception {
        // Arrange
        when(customerService.create(any())).thenReturn(sampleCustomer());

        // Act
        var request = post("/api/v1/customers").contentType(APPLICATION_JSON).content(VALID_BODY);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ada@example.com"));
    }

    @Test
    void should_return400_when_emailIsInvalid() throws Exception {
        // Arrange
        String body = """
                {
                  "firstName": "Ada",
                  "lastName": "Lovelace",
                  "email": "not-an-email"
                }
                """;

        // Act
        var request = post("/api/v1/customers").contentType(APPLICATION_JSON).content(body);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("email"));
    }

    @Test
    void should_return409_when_emailAlreadyExists() throws Exception {
        // Arrange
        when(customerService.create(any()))
                .thenThrow(new DuplicateResourceException("Customer email already exists: ada@example.com"));

        // Act
        var request = post("/api/v1/customers").contentType(APPLICATION_JSON).content(VALID_BODY);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isConflict());
    }

    // GET /api/v1/customers

    @Test
    void should_return200_withAllCustomers_when_listRequested() throws Exception {
        // Arrange
        when(customerService.findAll()).thenReturn(List.of(sampleCustomer()));

        // Act
        var request = get("/api/v1/customers");

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // GET /api/v1/customers/{id}

    @Test
    void should_return200_when_customerExists() throws Exception {
        // Arrange
        when(customerService.findById(1L)).thenReturn(sampleCustomer());

        // Act
        var request = get("/api/v1/customers/{id}", 1L);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ada"));
    }

    @Test
    void should_return404_when_customerDoesNotExist() throws Exception {
        // Arrange
        when(customerService.findById(99L)).thenThrow(new ResourceNotFoundException("Customer", 99L));

        // Act
        var request = get("/api/v1/customers/{id}", 99L);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isNotFound());
    }

    // PUT /api/v1/customers/{id}

    @Test
    void should_return200_when_updateRequestIsValid() throws Exception {
        // Arrange
        when(customerService.update(anyLong(), any())).thenReturn(sampleCustomer());

        // Act
        var request = put("/api/v1/customers/{id}", 1L).contentType(APPLICATION_JSON).content(VALID_BODY);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // DELETE /api/v1/customers/{id}

    @Test
    void should_return204_when_customerDeleted() throws Exception {
        // Arrange
        // service.delete is a void no-op by default on a mock

        // Act
        var request = delete("/api/v1/customers/{id}", 1L);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isNoContent());
    }

    @Test
    void should_return422_when_customerHasOrders() throws Exception {
        // Arrange
        doThrow(new BusinessRuleException("Customer 1 has orders and cannot be deleted"))
                .when(customerService).delete(1L);

        // Act
        var request = delete("/api/v1/customers/{id}", 1L);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isUnprocessableEntity());
    }
}
