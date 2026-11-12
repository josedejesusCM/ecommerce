package com.jjcm.ecommerce.api.controller;

import com.jjcm.ecommerce.api.dto.ReviewResponse;
import com.jjcm.ecommerce.api.exception.ResourceNotFoundException;
import com.jjcm.ecommerce.api.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    private static final String VALID_BODY = """
            {
              "customerId": 1,
              "rating": 4,
              "comment": "Works well"
            }
            """;

    private ReviewResponse sampleReview() {
        return ReviewResponse.builder()
                .id(1L)
                .productId(1L)
                .customerId(1L)
                .rating(4)
                .comment("Works well")
                .createdAt(LocalDateTime.of(2026, 11, 12, 10, 0))
                .build();
    }

    // POST /api/v1/products/{productId}/reviews

    @Test
    void should_return201_when_reviewIsValid() throws Exception {
        // Arrange
        when(reviewService.create(anyLong(), any())).thenReturn(sampleReview());

        // Act
        var request = post("/api/v1/products/1/reviews").contentType(APPLICATION_JSON).content(VALID_BODY);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(4));
    }

    @Test
    void should_return201_when_ratingIsAtLowerBound() throws Exception {
        // Arrange
        when(reviewService.create(anyLong(), any())).thenReturn(sampleReview());
        String body = VALID_BODY.replace("\"rating\": 4", "\"rating\": 1");

        // Act
        var request = post("/api/v1/products/1/reviews").contentType(APPLICATION_JSON).content(body);

        // Assert
        mockMvc.perform(request).andExpect(status().isCreated());
    }

    @Test
    void should_return201_when_ratingIsAtUpperBound() throws Exception {
        // Arrange
        when(reviewService.create(anyLong(), any())).thenReturn(sampleReview());
        String body = VALID_BODY.replace("\"rating\": 4", "\"rating\": 5");

        // Act
        var request = post("/api/v1/products/1/reviews").contentType(APPLICATION_JSON).content(body);

        // Assert
        mockMvc.perform(request).andExpect(status().isCreated());
    }

    @Test
    void should_return400_when_ratingIsBelowRange() throws Exception {
        // Arrange
        String body = VALID_BODY.replace("\"rating\": 4", "\"rating\": 0");

        // Act
        var request = post("/api/v1/products/1/reviews").contentType(APPLICATION_JSON).content(body);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("rating"));
    }

    @Test
    void should_return400_when_ratingIsAboveRange() throws Exception {
        // Arrange
        String body = VALID_BODY.replace("\"rating\": 4", "\"rating\": 6");

        // Act
        var request = post("/api/v1/products/1/reviews").contentType(APPLICATION_JSON).content(body);

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("rating"));
    }

    @Test
    void should_return404_when_productDoesNotExist() throws Exception {
        // Arrange
        doThrow(new ResourceNotFoundException("Product", 99L))
                .when(reviewService).create(anyLong(), any());

        // Act
        var request = post("/api/v1/products/99/reviews").contentType(APPLICATION_JSON).content(VALID_BODY);

        // Assert
        mockMvc.perform(request).andExpect(status().isNotFound());
    }

    // GET /api/v1/products/{productId}/reviews

    @Test
    void should_return200_when_productHasReviews() throws Exception {
        // Arrange
        when(reviewService.findByProduct(anyLong())).thenReturn(List.of(sampleReview()));

        // Act
        var request = get("/api/v1/products/1/reviews");

        // Assert
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].comment").value("Works well"));
    }

    @Test
    void should_return404_when_listingReviewsForMissingProduct() throws Exception {
        // Arrange
        doThrow(new ResourceNotFoundException("Product", 99L))
                .when(reviewService).findByProduct(anyLong());

        // Act
        var request = get("/api/v1/products/99/reviews");

        // Assert
        mockMvc.perform(request).andExpect(status().isNotFound());
    }
}
