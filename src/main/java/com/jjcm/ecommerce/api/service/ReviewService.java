package com.jjcm.ecommerce.api.service;

import com.jjcm.ecommerce.api.dto.ReviewRequest;
import com.jjcm.ecommerce.api.dto.ReviewResponse;
import com.jjcm.ecommerce.api.entity.Customer;
import com.jjcm.ecommerce.api.entity.Product;
import com.jjcm.ecommerce.api.entity.Review;
import com.jjcm.ecommerce.api.exception.ResourceNotFoundException;
import com.jjcm.ecommerce.api.repository.CustomerRepository;
import com.jjcm.ecommerce.api.repository.ProductRepository;
import com.jjcm.ecommerce.api.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    public List<ReviewResponse> findByProduct(Long productId) {
        ensureProductExists(productId);
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    @Transactional
    public ReviewResponse create(Long productId, ReviewRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", request.getCustomerId()));
        Review review = Review.builder()
                .product(product)
                .customer(customer)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();
        return ReviewResponse.from(reviewRepository.save(review));
    }

    private void ensureProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product", productId);
        }
    }
}
