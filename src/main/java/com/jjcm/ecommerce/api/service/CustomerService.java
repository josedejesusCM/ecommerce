package com.jjcm.ecommerce.api.service;

import com.jjcm.ecommerce.api.dto.CustomerRequest;
import com.jjcm.ecommerce.api.dto.CustomerResponse;
import com.jjcm.ecommerce.api.entity.Customer;
import com.jjcm.ecommerce.api.exception.BusinessRuleException;
import com.jjcm.ecommerce.api.exception.DuplicateResourceException;
import com.jjcm.ecommerce.api.exception.ResourceNotFoundException;
import com.jjcm.ecommerce.api.repository.CustomerRepository;
import com.jjcm.ecommerce.api.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    public List<CustomerResponse> findAll() {
        return customerRepository.findAll().stream().map(CustomerResponse::from).toList();
    }

    public CustomerResponse findById(Long id) {
        return CustomerResponse.from(getEntity(id));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        ensureUniqueEmail(request.getEmail(), null);
        Customer customer = Customer.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .build();
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = getEntity(id);
        ensureUniqueEmail(request.getEmail(), id);
        customer.setFirstName(request.getFirstName());
        customer.setLastName(request.getLastName());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        customer.setAddress(request.getAddress());
        return CustomerResponse.from(customer);
    }

    @Transactional
    public void delete(Long id) {
        Customer customer = getEntity(id);
        if (orderRepository.existsByCustomerId(id)) {
            throw new BusinessRuleException("Customer " + id + " has orders and cannot be deleted");
        }
        customerRepository.delete(customer);
    }

    private Customer getEntity(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    private void ensureUniqueEmail(String email, Long currentId) {
        customerRepository.findByEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Customer email already exists: " + email);
                });
    }
}
