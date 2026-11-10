package com.jjcm.ecommerce.api.service;

import com.jjcm.ecommerce.api.dto.OrderCreateRequest;
import com.jjcm.ecommerce.api.dto.OrderItemRequest;
import com.jjcm.ecommerce.api.dto.OrderResponse;
import com.jjcm.ecommerce.api.entity.Customer;
import com.jjcm.ecommerce.api.entity.Order;
import com.jjcm.ecommerce.api.entity.OrderItem;
import com.jjcm.ecommerce.api.entity.OrderStatus;
import com.jjcm.ecommerce.api.entity.Payment;
import com.jjcm.ecommerce.api.entity.PaymentMethod;
import com.jjcm.ecommerce.api.entity.PaymentStatus;
import com.jjcm.ecommerce.api.entity.Product;
import com.jjcm.ecommerce.api.exception.BusinessRuleException;
import com.jjcm.ecommerce.api.exception.ResourceNotFoundException;
import com.jjcm.ecommerce.api.repository.CustomerRepository;
import com.jjcm.ecommerce.api.repository.OrderRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private OrderService orderService;

    private Customer customer() {
        return Customer.builder().id(1L).firstName("John").lastName("Doe").email("john.doe@example.com").build();
    }

    private Product product(int stock) {
        return Product.builder()
                .id(1L)
                .sku("ELEC-MOU-001")
                .price(new BigDecimal("24.99"))
                .stockQuantity(stock)
                .active(true)
                .build();
    }

    private OrderCreateRequest placeOrderRequest(long customerId, int quantity) {
        return OrderCreateRequest.builder()
                .customerId(customerId)
                .shippingAddress("123 Maple Street, Springfield")
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .items(List.of(OrderItemRequest.builder().productId(1L).quantity(quantity).build()))
                .build();
    }

    private Order existingOrder(OrderStatus status, Product product, int quantity, PaymentStatus paymentStatus) {
        Order order = Order.builder().id(1L).customer(customer()).status(status).build();
        order.addItem(OrderItem.builder()
                .product(product)
                .quantity(quantity)
                .unitPrice(product.getPrice())
                .build());
        order.setPayment(Payment.builder()
                .order(order)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .paymentStatus(paymentStatus)
                .amount(BigDecimal.ZERO)
                .build());
        return order;
    }

    @Test
    void should_throwResourceNotFoundException_when_placingOrderForUnknownCustomer() {
        // Arrange
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        // Act
        Executable action = () -> orderService.create(placeOrderRequest(99L, 1));

        // Assert
        assertThrows(ResourceNotFoundException.class, action);
    }

    @Test
    void should_throwBusinessRuleException_when_requestedQuantityExceedsStock() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(2)));

        // Act
        Executable action = () -> orderService.create(placeOrderRequest(1L, 5));

        // Assert
        assertThrows(BusinessRuleException.class, action);
    }

    @Test
    void should_reduceStockByQuantity_when_orderPlaced() {
        // Arrange
        Product product = product(10);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        orderService.create(placeOrderRequest(1L, 3));

        // Assert
        assertEquals(7, product.getStockQuantity().intValue());
    }

    @Test
    void should_throwBusinessRuleException_when_cancellingDeliveredOrder() {
        // Arrange
        Order delivered = existingOrder(OrderStatus.DELIVERED, product(7), 3, PaymentStatus.COMPLETED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(delivered));

        // Act
        Executable action = () -> orderService.updateStatus(1L, OrderStatus.CANCELLED);

        // Assert
        assertThrows(BusinessRuleException.class, action);
    }

    @Test
    void should_throwBusinessRuleException_when_cancellingAlreadyCancelledOrder() {
        // Arrange
        Order cancelled = existingOrder(OrderStatus.CANCELLED, product(7), 3, PaymentStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(cancelled));

        // Act
        Executable action = () -> orderService.updateStatus(1L, OrderStatus.CANCELLED);

        // Assert
        assertThrows(BusinessRuleException.class, action);
    }

    @Test
    void should_restoreStock_when_orderCancelled() {
        // Arrange
        Product product = product(7);
        Order processing = existingOrder(OrderStatus.PROCESSING, product, 3, PaymentStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(processing));

        // Act
        orderService.updateStatus(1L, OrderStatus.CANCELLED);

        // Assert
        assertEquals(10, product.getStockQuantity().intValue());
    }

    @Test
    void should_markPaymentRefunded_when_cancellingOrderWithCompletedPayment() {
        // Arrange
        Order processing = existingOrder(OrderStatus.PROCESSING, product(7), 3, PaymentStatus.COMPLETED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(processing));

        // Act
        OrderResponse result = orderService.updateStatus(1L, OrderStatus.CANCELLED);

        // Assert
        assertEquals(PaymentStatus.REFUNDED, result.getPayment().getPaymentStatus());
    }
}
