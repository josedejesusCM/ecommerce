package com.jjcm.ecommerce.api.service;

import com.jjcm.ecommerce.api.dto.OrderCreateRequest;
import com.jjcm.ecommerce.api.dto.OrderItemRequest;
import com.jjcm.ecommerce.api.dto.OrderResponse;
import com.jjcm.ecommerce.api.entity.Customer;
import com.jjcm.ecommerce.api.entity.Order;
import com.jjcm.ecommerce.api.entity.OrderItem;
import com.jjcm.ecommerce.api.entity.OrderStatus;
import com.jjcm.ecommerce.api.entity.Payment;
import com.jjcm.ecommerce.api.entity.PaymentStatus;
import com.jjcm.ecommerce.api.entity.Product;
import com.jjcm.ecommerce.api.exception.BusinessRuleException;
import com.jjcm.ecommerce.api.exception.ResourceNotFoundException;
import com.jjcm.ecommerce.api.repository.CustomerRepository;
import com.jjcm.ecommerce.api.repository.OrderRepository;
import com.jjcm.ecommerce.api.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private static final int LOW_STOCK_THRESHOLD = 10;

    /** One-way status transitions. Statuses absent from this map (DELIVERED, CANCELLED, REFUNDED) are final. */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
            OrderStatus.PROCESSING, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED)
    );

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    /** Placing an order reduces stock immediately. */
    @Transactional
    public OrderResponse create(OrderCreateRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", request.getCustomerId()));

        Order order = Order.builder()
                .customer(customer)
                .status(OrderStatus.PENDING)
                .shippingAddress(request.getShippingAddress())
                .notes(request.getNotes())
                .build();

        Set<Long> seenProductIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {
            if (!seenProductIds.add(itemRequest.getProductId())) {
                throw new BusinessRuleException("Product " + itemRequest.getProductId() + " appears more than once in the order");
            }
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemRequest.getProductId()));
            if (!product.getActive()) {
                throw new BusinessRuleException("Product is not available: " + product.getSku());
            }

            int quantity = itemRequest.getQuantity();
            if (product.getStockQuantity() < quantity) {
                throw new BusinessRuleException("Insufficient stock for " + product.getSku()
                        + ": requested " + quantity + ", available " + product.getStockQuantity());
            }
            product.setStockQuantity(product.getStockQuantity() - quantity);

            order.addItem(OrderItem.builder()
                    .product(product)
                    .quantity(quantity)
                    .unitPrice(product.getPrice())
                    .build());
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        }

        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product.getStockQuantity() < LOW_STOCK_THRESHOLD) {
                log.warn("Low stock alert: {} has {} units left", product.getName(), product.getStockQuantity());
            }
        }

        order.setTotalAmount(total);
        order.setPayment(Payment.builder()
                .order(order)
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus(PaymentStatus.PENDING)
                .amount(total)
                .build());

        return OrderResponse.from(orderRepository.save(order));
    }

    public OrderResponse findById(Long id) {
        return OrderResponse.from(getEntity(id));
    }

    public List<OrderResponse> findAll(Long customerId, OrderStatus status) {
        List<Order> orders = customerId != null
                ? orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                : orderRepository.findAll();
        return orders.stream()
                .filter(order -> status == null || order.getStatus() == status)
                .map(OrderResponse::from)
                .toList();
    }

    /**
     * Enforces one-way transitions. Cancelling restores stock for every item and,
     * if the payment was completed, marks that payment REFUNDED.
     */
    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus target) {
        Order order = getEntity(id);
        OrderStatus current = order.getStatus();

        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new BusinessRuleException("Cannot change order status from " + current + " to " + target);
        }

        if (target == OrderStatus.CANCELLED) {
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            }
            Payment payment = order.getPayment();
            if (payment != null && payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
                payment.setPaymentStatus(PaymentStatus.REFUNDED);
            }
        }

        order.setStatus(target);
        return OrderResponse.from(order);
    }

    /**
     * Sets the payment status. No payment transition rules were specified, so any status is accepted,
     * except on cancelled orders, whose stock has already been returned.
     */
    @Transactional
    public OrderResponse updatePaymentStatus(Long id, PaymentStatus status) {
        Order order = getEntity(id);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot change the payment of a cancelled order");
        }
        order.getPayment().setPaymentStatus(status);
        return OrderResponse.from(order);
    }

    private Order getEntity(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }
}
