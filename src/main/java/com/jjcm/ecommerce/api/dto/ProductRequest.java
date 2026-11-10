package com.jjcm.ecommerce.api.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    private String description;

    @NotNull
    @Digits(integer = 8, fraction = 2)
    private BigDecimal price;

    @NotBlank
    @Size(max = 100)
    private String sku;

    /** Optional on create and update; defaults to 0 when omitted on create. */
    @PositiveOrZero
    private Integer stockQuantity;

    /** Optional; defaults to true when omitted on create. */
    private Boolean active;

    @NotNull
    private Long categoryId;
}
