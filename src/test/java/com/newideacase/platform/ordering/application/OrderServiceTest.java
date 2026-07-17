package com.newideacase.platform.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.newideacase.platform.catalog.application.ProductService;
import com.newideacase.platform.catalog.domain.Product;
import com.newideacase.platform.catalog.domain.ProductStatus;
import com.newideacase.platform.ordering.domain.Order;
import com.newideacase.platform.ordering.domain.OrderStatus;
import com.newideacase.platform.ordering.infrastructure.MongoOrderRepository;
import com.newideacase.platform.ordering.infrastructure.OrderDocument;
import com.newideacase.platform.shared.error.BadRequestException;
import com.newideacase.platform.shared.error.ConflictException;
import com.newideacase.platform.shared.security.RequestIdentity;
import com.newideacase.platform.shared.security.RequestIdentityProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private MongoOrderRepository repository;

    @Mock
    private ProductService productService;

    @Mock
    private RequestIdentityProvider identityProvider;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(repository, productService, identityProvider);
        org.mockito.Mockito.lenient().when(identityProvider.current()).thenReturn(
                new RequestIdentity("user-1", "tenant-1", Set.of()));
    }

    @Test
    void createsImmutableProductSnapshotAndTotal() {
        when(productService.get("product-1")).thenReturn(product("product-1", "10.50"));
        when(repository.save(org.mockito.ArgumentMatchers.any(OrderDocument.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        Order order = service.create(List.of(new CreateOrderItem("product-1", 3)));

        assertThat(order.totalAmount()).isEqualByComparingTo("31.50");
        assertThat(order.currency()).isEqualTo("TWD");
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.items().getFirst().sku()).isEqualTo("SKU-1");
    }

    @Test
    void rejectsDuplicateProductLines() {
        assertThatThrownBy(() -> service.create(List.of(
                new CreateOrderItem("product-1", 1),
                new CreateOrderItem("product-1", 2))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Duplicate");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsStaleStatusTransition() {
        OrderDocument document = persisted(new OrderDocument(
                "tenant-1", "user-1", List.of(), BigDecimal.TEN, "TWD"));
        ReflectionTestUtils.setField(document, "version", 2L);
        when(repository.findByIdAndTenantIdAndSubject("order-id", "tenant-1", "user-1"))
                .thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.updateStatus(
                "order-id", OrderStatus.CONFIRMED, 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified");
    }

    private static Product product(String id, String amount) {
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
        return new Product(
                id, "SKU-1", "Product", null, new BigDecimal(amount), "TWD",
                ProductStatus.ACTIVE, now, now, 0L);
    }

    private static OrderDocument persisted(OrderDocument document) {
        ReflectionTestUtils.setField(document, "id", "order-id");
        ReflectionTestUtils.setField(document, "createdAt", Instant.parse("2026-07-17T00:00:00Z"));
        ReflectionTestUtils.setField(document, "updatedAt", Instant.parse("2026-07-17T00:00:00Z"));
        ReflectionTestUtils.setField(document, "version", 0L);
        return document;
    }
}
