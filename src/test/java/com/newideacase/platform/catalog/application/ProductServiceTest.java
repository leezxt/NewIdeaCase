package com.newideacase.platform.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.newideacase.platform.catalog.domain.ProductCursor;
import com.newideacase.platform.catalog.domain.Product;
import com.newideacase.platform.catalog.domain.ProductStatus;
import com.newideacase.platform.catalog.infrastructure.MongoProductRepository;
import com.newideacase.platform.catalog.infrastructure.ProductDocument;
import com.newideacase.platform.catalog.infrastructure.ProductQueryRepository;
import com.newideacase.platform.shared.error.BadRequestException;
import com.newideacase.platform.shared.error.ConflictException;
import com.newideacase.platform.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private MongoProductRepository repository;

    @Mock
    private ProductQueryRepository queryRepository;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(repository, queryRepository);
    }

    @Test
    void createsNormalizedProduct() {
        CreateProductCommand command = new CreateProductCommand(
                " sku-001 ", " Product ", " Description ", new BigDecimal("1299.00"), "twd");
        when(repository.existsBySku("SKU-001")).thenReturn(false);
        when(repository.save(org.mockito.ArgumentMatchers.any(ProductDocument.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        Product result = service.create(command);

        assertThat(result.id()).isEqualTo("product-id");
        assertThat(result.sku()).isEqualTo("SKU-001");
        assertThat(result.name()).isEqualTo("Product");
        assertThat(result.currency()).isEqualTo("TWD");
        assertThat(result.status()).isEqualTo(ProductStatus.ACTIVE);
        verify(repository).save(org.mockito.ArgumentMatchers.any(ProductDocument.class));
    }

    @Test
    void rejectsDuplicateSkuBeforeInsert() {
        when(repository.existsBySku("SKU-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateProductCommand(
                "SKU-001", "Product", null, BigDecimal.TEN, "TWD")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("SKU-001");
    }

    @Test
    void reportsMissingProduct() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void updatesProductWhenVersionMatches() {
        ProductDocument document = persisted(new ProductDocument(
                "SKU-001", "Old name", null, BigDecimal.TEN, "TWD", ProductStatus.ACTIVE));
        when(repository.findById("product-id")).thenReturn(Optional.of(document));
        when(repository.save(document)).thenAnswer(invocation -> {
            ReflectionTestUtils.setField(document, "version", 1L);
            return document;
        });

        Product result = service.update("product-id", new UpdateProductCommand(
                "New name", null, false, new BigDecimal("20.00"), "usd", ProductStatus.INACTIVE, 0L));

        assertThat(result.name()).isEqualTo("New name");
        assertThat(result.amount()).isEqualByComparingTo("20.00");
        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.status()).isEqualTo(ProductStatus.INACTIVE);
        assertThat(result.version()).isEqualTo(1L);
    }

    @Test
    void rejectsStaleProductVersion() {
        ProductDocument document = persisted(new ProductDocument(
                "SKU-001", "Product", null, BigDecimal.TEN, "TWD", ProductStatus.ACTIVE));
        ReflectionTestUtils.setField(document, "version", 2L);
        when(repository.findById("product-id")).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.update("product-id", new UpdateProductCommand(
                "New name", null, false, null, null, null, 1L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified");
        verify(repository, never()).save(document);
    }

    @Test
    void createsAndConsumesOpaqueCursor() {
        ProductDocument newest = persistedDocument(
                "64b64b64b64b64b64b64b641", Instant.parse("2026-07-15T00:00:03Z"));
        ProductDocument second = persistedDocument(
                "64b64b64b64b64b64b64b640", Instant.parse("2026-07-15T00:00:02Z"));
        ProductDocument older = persistedDocument(
                "64b64b64b64b64b64b64b63f", Instant.parse("2026-07-15T00:00:01Z"));
        when(queryRepository.findPage(null, 3)).thenReturn(List.of(newest, second, older));

        ProductPage firstPage = service.list(null, 2);

        assertThat(firstPage.items()).extracting(Product::id)
                .containsExactly(newest.getId(), second.getId());
        assertThat(firstPage.nextCursor()).isNotBlank();

        clearInvocations(queryRepository);
        when(queryRepository.findPage(org.mockito.ArgumentMatchers.any(ProductCursor.class),
                org.mockito.ArgumentMatchers.eq(3))).thenReturn(List.of());
        service.list(firstPage.nextCursor(), 2);

        ArgumentCaptor<ProductCursor> cursor = ArgumentCaptor.forClass(ProductCursor.class);
        verify(queryRepository).findPage(cursor.capture(), org.mockito.ArgumentMatchers.eq(3));
        assertThat(cursor.getValue().id()).isEqualTo(second.getId());
        assertThat(cursor.getValue().createdAt()).isEqualTo(second.getCreatedAt());
    }

    @Test
    void clearsDescriptionExplicitly() {
        ProductDocument document = persisted(new ProductDocument(
                "SKU-001", "Product", "Description", BigDecimal.TEN, "TWD", ProductStatus.ACTIVE));
        when(repository.findById("product-id")).thenReturn(Optional.of(document));
        when(repository.save(document)).thenReturn(document);

        Product result = service.update("product-id", new UpdateProductCommand(
                null, null, true, null, null, null, 0L));

        assertThat(result.description()).isNull();
    }

    @Test
    void rejectsInvalidCursor() {
        assertThatThrownBy(() -> service.list("not-a-valid-cursor", 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cursor");
    }

    private static ProductDocument persisted(ProductDocument document) {
        ReflectionTestUtils.setField(document, "id", "product-id");
        ReflectionTestUtils.setField(document, "createdAt", Instant.parse("2026-07-15T00:00:00Z"));
        ReflectionTestUtils.setField(document, "updatedAt", Instant.parse("2026-07-15T00:00:00Z"));
        ReflectionTestUtils.setField(document, "version", 0L);
        return document;
    }

    private static ProductDocument persistedDocument(String id, Instant createdAt) {
        ProductDocument document = new ProductDocument(
                "SKU-" + id, "Product", null, BigDecimal.TEN, "TWD", ProductStatus.ACTIVE);
        ReflectionTestUtils.setField(document, "id", id);
        ReflectionTestUtils.setField(document, "createdAt", createdAt);
        ReflectionTestUtils.setField(document, "updatedAt", createdAt);
        ReflectionTestUtils.setField(document, "version", 0L);
        return document;
    }
}
