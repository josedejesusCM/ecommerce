package com.jjcm.ecommerce.api.controller;

import com.jjcm.ecommerce.api.dto.CategoryRequest;
import com.jjcm.ecommerce.api.dto.CategoryResponse;
import com.jjcm.ecommerce.api.exception.ResourceNotFoundException;
import com.jjcm.ecommerce.api.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CategoryControllerTest {

    private static final String VALID_BODY = """
            {"name": "Books", "slug": "books", "description": "Printed books"}
            """;

    private final MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    CategoryControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void should_return201_when_category_is_valid() throws Exception {
        // Arrange
        CategoryResponse created = CategoryResponse.builder().id(1L).name("Books").slug("books").build();
        when(categoryService.create(any(CategoryRequest.class))).thenReturn(created);

        // Act
        var result = mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY));

        // Assert
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.slug").value("books"));
    }

    @Test
    void should_return400_when_name_is_blank() throws Exception {
        // Arrange
        String body = """
                {"name": "", "slug": "books"}
                """;

        // Act
        var result = mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("name"));
    }

    @Test
    void should_return400_when_slug_is_not_lowercase_kebab_case() throws Exception {
        // Arrange
        String body = """
                {"name": "Books", "slug": "Not A Slug"}
                """;

        // Act
        var result = mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("slug"));
    }

    @Test
    void should_return400_when_body_is_malformed_json() throws Exception {
        // Arrange
        String body = "{not json";

        // Act
        var result = mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void should_return200_when_categories_exist() throws Exception {
        // Arrange
        when(categoryService.findAll()).thenReturn(List.of(
                CategoryResponse.builder().id(1L).name("Books").slug("books").build(),
                CategoryResponse.builder().id(2L).name("Music").slug("music").build()));

        // Act
        var result = mockMvc.perform(get("/api/v1/categories"));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void should_return200_when_category_exists() throws Exception {
        // Arrange
        when(categoryService.findById(1L))
                .thenReturn(CategoryResponse.builder().id(1L).name("Books").slug("books").build());

        // Act
        var result = mockMvc.perform(get("/api/v1/categories/1"));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Books"));
    }

    @Test
    void should_return404_when_category_does_not_exist() throws Exception {
        // Arrange
        when(categoryService.findById(99L)).thenThrow(new ResourceNotFoundException("Category", 99L));

        // Act
        var result = mockMvc.perform(get("/api/v1/categories/99"));

        // Assert
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void should_return200_when_update_is_valid() throws Exception {
        // Arrange
        when(categoryService.update(anyLong(), any(CategoryRequest.class)))
                .thenReturn(CategoryResponse.builder().id(1L).name("Books").slug("books").build());

        // Act
        var result = mockMvc.perform(put("/api/v1/categories/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void should_return204_when_category_is_deleted() throws Exception {
        // Arrange
        // (no stubbing needed: delete returns void)

        // Act
        var result = mockMvc.perform(delete("/api/v1/categories/1"));

        // Assert
        result.andExpect(status().isNoContent());
        verify(categoryService).delete(1L);
    }
}
