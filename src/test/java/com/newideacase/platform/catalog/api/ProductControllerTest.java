package com.newideacase.platform.catalog.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.newideacase.platform.catalog.application.ProductService;
import com.newideacase.platform.catalog.application.ProductPage;
import com.newideacase.platform.catalog.domain.Product;
import com.newideacase.platform.catalog.domain.ProductStatus;
import com.newideacase.platform.shared.error.ApiExceptionHandler;
import com.newideacase.platform.shared.web.CorrelationIdFilter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductControllerTest {

    private ProductService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(ProductService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilter(new CorrelationIdFilter())
                .build();
    }

    @Test
    void createsProductAndReturnsLocation() throws Exception {
        when(service.create(any())).thenReturn(new Product(
                "product-id", "SKU-001", "Product", null, new BigDecimal("1299.00"), "TWD",
                ProductStatus.ACTIVE, Instant.parse("2026-07-15T00:00:00Z"),
                Instant.parse("2026-07-15T00:00:00Z"), 0L));

        mockMvc.perform(post("/api/v1/products")
                        .header(CorrelationIdFilter.HEADER_NAME, "test-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "SKU-001",
                                  "name": "Product",
                                  "amount": 1299.00,
                                  "currency": "TWD"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/products/product-id"))
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "test-request"))
                .andExpect(jsonPath("$.sku").value("SKU-001"));
    }

    @Test
    void returnsProblemDetailsForInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"", "name":"", "amount":0, "currency":"NT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type")
                        .value("https://api.newideacase.local/problems/validation-error"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME));
    }

    @Test
    void listsProductsWithNextCursor() throws Exception {
        when(service.list(null, 2)).thenReturn(new ProductPage(List.of(product(0L)), "next-token"));

        mockMvc.perform(get("/api/v1/products").queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("product-id"))
                .andExpect(jsonPath("$.nextCursor").value("next-token"));
    }

    @Test
    void updatesProductWithVersion() throws Exception {
        when(service.update(org.mockito.ArgumentMatchers.eq("product-id"), any()))
                .thenReturn(product(1L));

        mockMvc.perform(patch("/api/v1/products/product-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated product", "version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    private static Product product(long version) {
        return new Product(
                "product-id", "SKU-001", "Product", null, new BigDecimal("1299.00"), "TWD",
                ProductStatus.ACTIVE, Instant.parse("2026-07-15T00:00:00Z"),
                Instant.parse("2026-07-15T00:00:00Z"), version);
    }
}
