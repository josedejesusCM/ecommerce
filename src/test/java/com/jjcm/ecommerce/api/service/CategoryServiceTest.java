package com.jjcm.ecommerce.api.service;

import com.jjcm.ecommerce.api.dto.CategoryRequest;
import com.jjcm.ecommerce.api.dto.CategoryResponse;
import com.jjcm.ecommerce.api.entity.Category;
import com.jjcm.ecommerce.api.exception.BusinessRuleException;
import com.jjcm.ecommerce.api.exception.DuplicateResourceException;
import com.jjcm.ecommerce.api.exception.ResourceNotFoundException;
import com.jjcm.ecommerce.api.repository.CategoryRepository;
import com.jjcm.ecommerce.api.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryService categoryService;

    private CategoryRequest request() {
        return CategoryRequest.builder()
                .name("Books")
                .slug("books")
                .description("Printed books")
                .build();
    }

    @Test
    void should_throwDuplicateResourceException_when_categoryNameAlreadyExists() {
        // Arrange
        Category existing = Category.builder().id(2L).name("Books").slug("books-old").build();
        when(categoryRepository.findByNameIgnoreCase("Books")).thenReturn(Optional.of(existing));

        // Act
        Executable action = () -> categoryService.create(request());

        // Assert
        assertThrows(DuplicateResourceException.class, action);
    }

    @Test
    void should_returnCategory_when_createdWithUniqueNameAndSlug() {
        // Arrange
        when(categoryRepository.findByNameIgnoreCase("Books")).thenReturn(Optional.empty());
        when(categoryRepository.findBySlug("books")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        // Act
        CategoryResponse result = categoryService.create(request());

        // Assert
        assertEquals("books", result.getSlug());
    }

    @Test
    void should_throwBusinessRuleException_when_deletingCategoryThatHasProducts() {
        // Arrange
        Category category = Category.builder().id(1L).name("Books").slug("books").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryId(1L)).thenReturn(true);

        // Act
        Executable action = () -> categoryService.delete(1L);

        // Assert
        assertThrows(BusinessRuleException.class, action);
    }

    @Test
    void should_throwResourceNotFoundException_when_categoryDoesNotExist() {
        // Arrange
        when(categoryRepository.findById(9L)).thenReturn(Optional.empty());

        // Act
        Executable action = () -> categoryService.findById(9L);

        // Assert
        assertThrows(ResourceNotFoundException.class, action);
    }
}
