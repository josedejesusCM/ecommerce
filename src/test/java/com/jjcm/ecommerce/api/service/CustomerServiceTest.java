package com.jjcm.ecommerce.api.service;

import com.jjcm.ecommerce.api.dto.CustomerRequest;
import com.jjcm.ecommerce.api.dto.CustomerResponse;
import com.jjcm.ecommerce.api.entity.Customer;
import com.jjcm.ecommerce.api.exception.BusinessRuleException;
import com.jjcm.ecommerce.api.exception.DuplicateResourceException;
import com.jjcm.ecommerce.api.exception.ResourceNotFoundException;
import com.jjcm.ecommerce.api.repository.CustomerRepository;
import com.jjcm.ecommerce.api.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private CustomerService customerService;

    private CustomerRequest request() {
        return CustomerRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .phone("+1-202-555-0101")
                .address("123 Maple Street, Springfield")
                .build();
    }

    @Test
    void should_throwDuplicateResourceException_when_emailAlreadyRegistered() {
        // Arrange
        Customer existing = Customer.builder().id(2L).email("john.doe@example.com").build();
        when(customerRepository.findByEmailIgnoreCase("john.doe@example.com")).thenReturn(Optional.of(existing));

        // Act
        Executable action = () -> customerService.create(request());

        // Assert
        assertThrows(DuplicateResourceException.class, action);
    }

    @Test
    void should_returnCustomer_when_registeredWithNewEmail() {
        // Arrange
        when(customerRepository.findByEmailIgnoreCase("john.doe@example.com")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        // Act
        CustomerResponse result = customerService.create(request());

        // Assert
        assertEquals("John", result.getFirstName());
    }

    @Test
    void should_throwBusinessRuleException_when_deletingCustomerWithOrders() {
        // Arrange
        Customer customer = Customer.builder().id(1L).email("john.doe@example.com").build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(orderRepository.existsByCustomerId(1L)).thenReturn(true);

        // Act
        Executable action = () -> customerService.delete(1L);

        // Assert
        assertThrows(BusinessRuleException.class, action);
    }

    @Test
    void should_throwResourceNotFoundException_when_customerDoesNotExist() {
        // Arrange
        when(customerRepository.findById(9L)).thenReturn(Optional.empty());

        // Act
        Executable action = () -> customerService.findById(9L);

        // Assert
        assertThrows(ResourceNotFoundException.class, action);
    }

    @Test
    void should_returnAllCustomers_when_listRequested() {
        // Arrange
        Customer customer = Customer.builder().id(1L).firstName("John").lastName("Doe").email("john.doe@example.com").build();
        when(customerRepository.findAll()).thenReturn(List.of(customer));

        // Act
        List<CustomerResponse> result = customerService.findAll();

        // Assert
        assertEquals(1, result.size());
    }

    @Test
    void should_returnCustomer_when_customerExists() {
        // Arrange
        Customer customer = Customer.builder().id(1L).firstName("John").lastName("Doe").email("john.doe@example.com").build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        // Act
        CustomerResponse result = customerService.findById(1L);

        // Assert
        assertEquals("Doe", result.getLastName());
    }

    @Test
    void should_updateFields_when_emailIsFree() {
        // Arrange
        Customer customer = Customer.builder().id(1L).firstName("Old").lastName("Name").email("old@example.com").build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.findByEmailIgnoreCase("john.doe@example.com")).thenReturn(Optional.empty());

        // Act
        CustomerResponse result = customerService.update(1L, request());

        // Assert
        assertEquals("John", result.getFirstName());
    }

    @Test
    void should_notThrow_when_updatingCustomerWithItsOwnEmail() {
        // Arrange
        Customer customer = Customer.builder().id(1L).firstName("John").lastName("Doe").email("john.doe@example.com").build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.findByEmailIgnoreCase("john.doe@example.com")).thenReturn(Optional.of(customer));

        // Act
        Executable action = () -> customerService.update(1L, request());

        // Assert
        assertDoesNotThrow(action);
    }

    @Test
    void should_throwDuplicateResourceException_when_updatedEmailBelongsToAnotherCustomer() {
        // Arrange
        Customer customer = Customer.builder().id(1L).firstName("Old").lastName("Name").email("old@example.com").build();
        Customer other = Customer.builder().id(2L).email("john.doe@example.com").build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.findByEmailIgnoreCase("john.doe@example.com")).thenReturn(Optional.of(other));

        // Act
        Executable action = () -> customerService.update(1L, request());

        // Assert
        assertThrows(DuplicateResourceException.class, action);
    }

    @Test
    void should_deleteCustomer_when_customerHasNoOrders() {
        // Arrange
        Customer customer = Customer.builder().id(1L).email("john.doe@example.com").build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(orderRepository.existsByCustomerId(1L)).thenReturn(false);

        // Act
        customerService.delete(1L);

        // Assert
        verify(customerRepository).delete(customer);
    }
}
