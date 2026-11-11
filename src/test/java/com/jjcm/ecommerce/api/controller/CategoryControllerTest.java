package com.jjcm.ecommerce.api.controller;

import com.jjcm.ecommerce.api.dto.CategoryResponse;
import com.jjcm.ecommerce.api.exception.BusinessRuleException;
import com.jjcm.ecommerce.api.exception.DuplicateResourceException;
import com.jjcm.ecommerce.api.exception.ResourceNotFoundException;
import com.jjcm.ecommerce.api.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    private static final String VALID_BODY = """
            {
              "name": "Books",
              "slug": "books",
              "description": "Printed books"
            }
            """;

    private CategoryResponse sampleCategory() {
        return CategoryResponse.builder()
                .id(1L)
                .name("Books")
                .slug("books")
                .description("Printed books")
                .build();
    }

    // POST /api/v1/categories

    @Test
    void should_return201_when_createRequestIsValid() throws Exception {
        // Arrange
        when(categoryService.create(any())).thenReturn(sampleCategory());

        // Act
        var request = post("/api/v1/categories").contentType(APPLICATION_JSON).content(VALID_BODY);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("books"));
    }

    @Test
    void should_return400_listingAllFieldErrors_when_createRequestIsInvalid() throws Exception {
        // Arrange
        String body = """
                {
                  "name": "",
                  "slug": "Not A Slug!"
                }
                """;

        // Act
        var request = post("/api/v1/categories").contentType(APPLICATION_JSON).content(body);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.length()").value(2));
    }

    @Test
    void should_return409_when_categoryNameAlreadyExists() throws Exception {
        // Arrange
        when(categoryService.create(any()))
                .thenThrow(new DuplicateResourceException("Category name already exists: Books"));

        // Act
        var request = post("/api/v1/categories").contentType(APPLICATION_JSON).content(VALID_BODY);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isConflict());
    }

    // GET /api/v1/categories

    @Test
    void should_return200_withAllCategories_when_listRequested() throws Exception {
        // Arrange
        when(categoryService.findAll()).thenReturn(List.of(sampleCategory()));

        // Act
        var request = get("/api/v1/categories");

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // GET /api/v1/categories/{id}

    @Test
    void should_return200_when_categoryExists() throws Exception {
        // Arrange
        when(categoryService.findById(1L)).thenReturn(sampleCategory());

        // Act
        var request = get("/api/v1/categories/{id}", 1L);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Books"));
    }

    @Test
    void should_return404_when_categoryDoesNotExist() throws Exception {
        // Arrange
        when(categoryService.findById(99L)).thenThrow(new ResourceNotFoundException("Category", 99L));

        // Act
        var request = get("/api/v1/categories/{id}", 99L);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isNotFound());
    }

    // PUT /api/v1/categories/{id}

    @Test
    void should_return200_when_updateRequestIsValid() throws Exception {
        // Arrange
        when(categoryService.update(anyLong(), any())).thenReturn(sampleCategory());

        // Act
        var request = put("/api/v1/categories/{id}", 1L).contentType(APPLICATION_JSON).content(VALID_BODY);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void should_return409_when_updatedSlugAlreadyExists() throws Exception {
        // Arrange
        when(categoryService.update(anyLong(), any()))
                .thenThrow(new DuplicateResourceException("Category slug already exists: books"));

        // Act
        var request = put("/api/v1/categories/{id}", 1L).contentType(APPLICATION_JSON).content(VALID_BODY);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isConflict());
    }

    // DELETE /api/v1/categories/{id}

    @Test
    void should_return204_when_categoryDeleted() throws Exception {
        // Arrange
        // service.delete is a void no-op by default on a mock

        // Act
        var request = delete("/api/v1/categories/{id}", 1L);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isNoContent());
    }

    @Test
    void should_return422_when_categoryHasProducts() throws Exception {
        // Arrange
        org.mockito.Mockito.doThrow(new BusinessRuleException("Category 1 still has products and cannot be deleted"))
                .when(categoryService).delete(1L);

        // Act
        var request = delete("/api/v1/categories/{id}", 1L);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isUnprocessableEntity());
    }
}
