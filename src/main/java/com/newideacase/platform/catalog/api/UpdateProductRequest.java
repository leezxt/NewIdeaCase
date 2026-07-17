package com.newideacase.platform.catalog.api;

import com.newideacase.platform.catalog.domain.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateProductRequest(
        @Size(min = 1, max = 200) String name,
        @Size(max = 4000) String description,
        Boolean clearDescription,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 16, fraction = 2) BigDecimal amount,
        @Pattern(regexp = "[A-Za-z]{3}") String currency,
        ProductStatus status,
        @PositiveOrZero Long version) {
}
