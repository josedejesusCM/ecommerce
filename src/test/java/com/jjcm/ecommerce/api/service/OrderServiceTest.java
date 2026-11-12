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
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(OrderService.class)).addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        ((Logger) LoggerFactory.getLogger(OrderService.class)).detachAppender(logAppender);
    }

    private Customer customer() {
        return Customer.builder().id(1L).firstName("John").lastName("Doe").email("john.doe@example.com").build();
    }

    private Product product(int stock) {
        return Product.builder()
                .id(1L)
                .name("Wireless Mouse")
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

    @Test
    void should_logLowStockWarning_when_remainingStockBelowThreshold() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(10)));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        orderService.create(placeOrderRequest(1L, 1));

        // Assert
        assertEquals("Low stock alert: Wireless Mouse has 9 units left",
                logAppender.list.get(0).getFormattedMessage());
    }

    @Test
    void should_logWarningAtWarnLevel_when_remainingStockBelowThreshold() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(10)));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        orderService.create(placeOrderRequest(1L, 1));

        // Assert
        assertEquals(Level.WARN, logAppender.list.get(0).getLevel());
    }

    @Test
    void should_notLogWarning_when_remainingStockEqualsThreshold() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(13)));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        orderService.create(placeOrderRequest(1L, 3));

        // Assert
        assertEquals(0, logAppender.list.size());
    }

    @Test
    void should_completeOrderWithoutException_when_remainingStockBelowThreshold() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(10)));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Executable action = () -> orderService.create(placeOrderRequest(1L, 1));

        // Assert
        assertDoesNotThrow(action);
    }

    @Test
    void should_throwBusinessRuleException_when_transitionNotAllowed() {
        // Arrange
        Order pending = existingOrder(OrderStatus.PENDING, product(7), 3, PaymentStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pending));

        // Act
        Executable action = () -> orderService.updateStatus(1L, OrderStatus.SHIPPED);

        // Assert
        assertThrows(BusinessRuleException.class, action);
    }

    @Test
    void should_leavePaymentUnchanged_when_cancellingOrderWithUncompletedPayment() {
        // Arrange
        Order processing = existingOrder(OrderStatus.PROCESSING, product(7), 3, PaymentStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(processing));

        // Act
        OrderResponse result = orderService.updateStatus(1L, OrderStatus.CANCELLED);

        // Assert
        assertEquals(PaymentStatus.PENDING, result.getPayment().getPaymentStatus());
    }

    @Test
    void should_returnOnlyMatchingStatus_when_filteringByCustomerAndStatus() {
        // Arrange
        Order pending = existingOrder(OrderStatus.PENDING, product(7), 1, PaymentStatus.PENDING);
        Order confirmed = existingOrder(OrderStatus.CONFIRMED, product(7), 1, PaymentStatus.PENDING);
        when(orderRepository.findByCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(pending, confirmed));

        // Act
        List<OrderResponse> result = orderService.findAll(1L, OrderStatus.PENDING);

        // Assert
        assertEquals(1, result.size());
    }

    @Test
    void should_returnAllOrders_when_noFiltersGiven() {
        // Arrange
        Order pending = existingOrder(OrderStatus.PENDING, product(7), 1, PaymentStatus.PENDING);
        Order confirmed = existingOrder(OrderStatus.CONFIRMED, product(7), 1, PaymentStatus.PENDING);
        when(orderRepository.findAll()).thenReturn(List.of(pending, confirmed));

        // Act
        List<OrderResponse> result = orderService.findAll(null, null);

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    void should_returnOrder_when_orderExists() {
        // Arrange
        when(orderRepository.findById(1L)).thenReturn(Optional.of(
                existingOrder(OrderStatus.PENDING, product(7), 1, PaymentStatus.PENDING)));

        // Act
        OrderResponse result = orderService.findById(1L);

        // Assert
        assertEquals(OrderStatus.PENDING, result.getStatus());
    }

    @Test
    void should_setPaymentStatus_when_orderNotCancelled() {
        // Arrange
        Order processing = existingOrder(OrderStatus.PROCESSING, product(7), 1, PaymentStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(processing));

        // Act
        OrderResponse result = orderService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);

        // Assert
        assertEquals(PaymentStatus.COMPLETED, result.getPayment().getPaymentStatus());
    }

    @Test
    void should_throwBusinessRuleException_when_changingPaymentOfCancelledOrder() {
        // Arrange
        Order cancelled = existingOrder(OrderStatus.CANCELLED, product(7), 1, PaymentStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(cancelled));

        // Act
        Executable action = () -> orderService.updatePaymentStatus(1L, PaymentStatus.COMPLETED);

        // Assert
        assertThrows(BusinessRuleException.class, action);
    }

    @Test
    void should_throwBusinessRuleException_when_sameProductAppearsTwice() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(10)));
        OrderCreateRequest request = OrderCreateRequest.builder()
                .customerId(1L)
                .shippingAddress("123 Maple Street, Springfield")
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .items(List.of(
                        OrderItemRequest.builder().productId(1L).quantity(1).build(),
                        OrderItemRequest.builder().productId(1L).quantity(2).build()))
                .build();

        // Act
        Executable action = () -> orderService.create(request);

        // Assert
        assertThrows(BusinessRuleException.class, action);
    }

    @Test
    void should_throwBusinessRuleException_when_productIsInactive() {
        // Arrange
        Product inactive = Product.builder()
                .id(1L).name("Wireless Mouse").sku("ELEC-MOU-001")
                .price(new BigDecimal("24.99")).stockQuantity(10).active(false).build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(productRepository.findById(1L)).thenReturn(Optional.of(inactive));

        // Act
        Executable action = () -> orderService.create(placeOrderRequest(1L, 1));

        // Assert
        assertThrows(BusinessRuleException.class, action);
    }

    @Test
    void should_throwResourceNotFoundException_when_orderItemProductDoesNotExist() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        // Act
        Executable action = () -> orderService.create(placeOrderRequest(1L, 1));

        // Assert
        assertThrows(ResourceNotFoundException.class, action);
    }
}
