package com.jjcm.ecommerce.api.repository;

import com.jjcm.ecommerce.api.entity.Order;
import com.jjcm.ecommerce.api.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Order> findByStatus(OrderStatus status);

    boolean existsByCustomerId(Long customerId);
}
