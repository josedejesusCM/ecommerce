package com.jjcm.ecommerce.api.service;

import com.jjcm.ecommerce.api.dto.CategoryRequest;
import com.jjcm.ecommerce.api.dto.CategoryResponse;
import com.jjcm.ecommerce.api.entity.Category;
import com.jjcm.ecommerce.api.exception.BusinessRuleException;
import com.jjcm.ecommerce.api.exception.DuplicateResourceException;
import com.jjcm.ecommerce.api.exception.ResourceNotFoundException;
import com.jjcm.ecommerce.api.repository.CategoryRepository;
import com.jjcm.ecommerce.api.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream().map(CategoryResponse::from).toList();
    }

    public CategoryResponse findById(Long id) {
        return CategoryResponse.from(getEntity(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        ensureUnique(request, null);
        Category category = Category.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .description(request.getDescription())
                .build();
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getEntity(id);
        ensureUnique(request, id);
        category.setName(request.getName());
        category.setSlug(request.getSlug());
        category.setDescription(request.getDescription());
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = getEntity(id);
        if (productRepository.existsByCategoryId(id)) {
            throw new BusinessRuleException("Category " + id + " still has products and cannot be deleted");
        }
        categoryRepository.delete(category);
    }

    private Category getEntity(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private void ensureUnique(CategoryRequest request, Long currentId) {
        categoryRepository.findByNameIgnoreCase(request.getName())
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Category name already exists: " + request.getName());
                });
        categoryRepository.findBySlug(request.getSlug())
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Category slug already exists: " + request.getSlug());
                });
    }
}
