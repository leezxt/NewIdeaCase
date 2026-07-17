package com.newideacase.platform.catalog.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.newideacase.platform.catalog.application.CreateProductCommand;
import com.newideacase.platform.catalog.application.ProductPage;
import com.newideacase.platform.catalog.application.ProductService;
import com.newideacase.platform.catalog.application.UpdateProductCommand;
import com.newideacase.platform.catalog.domain.Product;
import com.newideacase.platform.shared.error.ConflictException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class ProductMongoIntegrationTest {

    @Container
    static final MongoDBContainer MONGODB = new MongoDBContainer("mongo:8.0").withReplicaSet();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGODB.getReplicaSetUrl("new_idea_case_test"));
        registry.add("spring.data.mongodb.auto-index-creation", () -> true);
    }

    @Autowired
    private ProductService service;

    @Autowired
    private MongoProductRepository repository;

    @BeforeEach
    void clearProducts() {
        repository.deleteAll();
    }

    @Test
    void paginatesWithoutDuplicatesAndEnforcesOptimisticLocking() throws Exception {
        Product first = create("SKU-001");
        Thread.sleep(2);
        Product second = create("SKU-002");
        Thread.sleep(2);
        Product third = create("SKU-003");

        ProductPage firstPage = service.list(null, 2);
        ProductPage secondPage = service.list(firstPage.nextCursor(), 2);

        assertThat(firstPage.items()).extracting(Product::id)
                .containsExactly(third.id(), second.id());
        assertThat(firstPage.nextCursor()).isNotBlank();
        assertThat(secondPage.items()).extracting(Product::id).containsExactly(first.id());
        assertThat(secondPage.nextCursor()).isNull();

        Product updated = service.update(first.id(), new UpdateProductCommand(
                "Updated", null, false, null, null, null, first.version()));
        assertThat(updated.version()).isEqualTo(first.version() + 1);
        assertThatThrownBy(() -> service.update(first.id(), new UpdateProductCommand(
                "Stale update", null, false, null, null, null, first.version())))
                .isInstanceOf(ConflictException.class);
    }

    private Product create(String sku) {
        return service.create(new CreateProductCommand(
                sku, "Product " + sku, null, new BigDecimal("100.00"), "TWD"));
    }
}
