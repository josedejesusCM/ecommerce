package com.jjcm.ecommerce.api.service;

import com.jjcm.ecommerce.api.dto.ProductRequest;
import com.jjcm.ecommerce.api.dto.ProductResponse;
import com.jjcm.ecommerce.api.entity.Category;
import com.jjcm.ecommerce.api.entity.Product;
import com.jjcm.ecommerce.api.exception.BusinessRuleException;
import com.jjcm.ecommerce.api.exception.DuplicateResourceException;
import com.jjcm.ecommerce.api.exception.ResourceNotFoundException;
import com.jjcm.ecommerce.api.repository.CategoryRepository;
import com.jjcm.ecommerce.api.repository.OrderItemRepository;
import com.jjcm.ecommerce.api.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderItemRepository orderItemRepository;

    public List<ProductResponse> findAll() {
        return productRepository.findAll().stream().map(ProductResponse::from).toList();
    }

    public ProductResponse findById(Long id) {
        return ProductResponse.from(getEntity(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        ensureUniqueSku(request.getSku(), null);
        Category category = getCategory(request.getCategoryId());
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .sku(request.getSku())
                .stockQuantity(request.getStockQuantity() == null ? 0 : request.getStockQuantity())
                .active(request.getActive() == null ? Boolean.TRUE : request.getActive())
                .category(category)
                .build();
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getEntity(id);
        ensureUniqueSku(request.getSku(), id);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setSku(request.getSku());
        if (request.getStockQuantity() != null) {
            product.setStockQuantity(request.getStockQuantity());
        }
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }
        product.setCategory(getCategory(request.getCategoryId()));
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(Long id) {
        Product product = getEntity(id);
        if (orderItemRepository.existsByProductId(id)) {
            throw new BusinessRuleException("Product " + id + " appears in orders and cannot be deleted");
        }
        productRepository.delete(product);
    }

    private Product getEntity(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private Category getCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }

    private void ensureUniqueSku(String sku, Long currentId) {
        productRepository.findBySku(sku)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Product SKU already exists: " + sku);
                });
    }
}
