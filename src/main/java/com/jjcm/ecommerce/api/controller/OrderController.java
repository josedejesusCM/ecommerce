package com.jjcm.ecommerce.api.controller;

import com.jjcm.ecommerce.api.dto.OrderCreateRequest;
import com.jjcm.ecommerce.api.dto.OrderResponse;
import com.jjcm.ecommerce.api.dto.OrderStatusUpdateRequest;
import com.jjcm.ecommerce.api.dto.PaymentStatusUpdateRequest;
import com.jjcm.ecommerce.api.entity.OrderStatus;
import com.jjcm.ecommerce.api.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> findAll(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) OrderStatus status) {
        return ResponseEntity.ok(orderService.findAll(customerId, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.findById(id));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable Long id,
                                                      @Valid @RequestBody OrderStatusUpdateRequest request) {
        return ResponseEntity.ok(orderService.updateStatus(id, request.getStatus()));
    }

    @PutMapping("/{id}/payment")
    public ResponseEntity<OrderResponse> updatePayment(@PathVariable Long id,
                                                       @Valid @RequestBody PaymentStatusUpdateRequest request) {
        return ResponseEntity.ok(orderService.updatePaymentStatus(id, request.getPaymentStatus()));
    }
}
