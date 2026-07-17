package com.newideacase.platform.catalog.application;

import com.newideacase.platform.catalog.domain.Product;
import com.newideacase.platform.catalog.domain.ProductCursor;
import com.newideacase.platform.catalog.domain.ProductStatus;
import com.newideacase.platform.catalog.infrastructure.MongoProductRepository;
import com.newideacase.platform.catalog.infrastructure.ProductDocument;
import com.newideacase.platform.catalog.infrastructure.ProductQueryRepository;
import com.newideacase.platform.shared.error.BadRequestException;
import com.newideacase.platform.shared.error.ConflictException;
import com.newideacase.platform.shared.error.ResourceNotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private final MongoProductRepository repository;
    private final ProductQueryRepository queryRepository;

    public ProductService(MongoProductRepository repository, ProductQueryRepository queryRepository) {
        this.repository = repository;
        this.queryRepository = queryRepository;
    }

    @CachePut(cacheNames = "products", key = "#result.id")
    public Product create(CreateProductCommand command) {
        String sku = command.sku().trim().toUpperCase(Locale.ROOT);
        if (repository.existsBySku(sku)) {
            throw new ConflictException("Product SKU already exists: " + sku);
        }

        ProductDocument document = new ProductDocument(
                sku,
                command.name().trim(),
                command.description() == null ? null : command.description().trim(),
                command.amount(),
                command.currency().toUpperCase(Locale.ROOT),
                ProductStatus.ACTIVE);
        try {
            return toDomain(repository.save(document));
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Product SKU already exists: " + sku, exception);
        }
    }

    @Cacheable(cacheNames = "products", key = "#id")
    public Product get(String id) {
        return repository.findById(id)
                .map(ProductService::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    @CachePut(cacheNames = "products", key = "#result.id")
    public Product update(String id, UpdateProductCommand command) {
        if (command.version() == null) {
            throw new BadRequestException("Product version is required");
        }
        if (command.description() != null && command.clearDescription()) {
            throw new BadRequestException("Description and clearDescription cannot be supplied together");
        }
        if (command.name() == null && command.description() == null && !command.clearDescription() && command.amount() == null
                && command.currency() == null && command.status() == null) {
            throw new BadRequestException("At least one product field must be supplied");
        }

        ProductDocument document = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        if (!Objects.equals(document.getVersion(), command.version())) {
            throw new ConflictException("Product was modified by another request");
        }

        document.update(
                command.name() == null ? null : command.name().trim(),
                command.description() == null ? null : command.description().trim(),
                command.clearDescription(),
                command.amount(),
                command.currency() == null ? null : command.currency().toUpperCase(Locale.ROOT),
                command.status());
        try {
            return toDomain(repository.save(document));
        } catch (OptimisticLockingFailureException exception) {
            throw new ConflictException("Product was modified by another request", exception);
        }
    }

    public ProductPage list(String encodedCursor, int limit) {
        if (limit < 1 || limit > 100) {
            throw new BadRequestException("Limit must be between 1 and 100");
        }
        ProductCursor cursor = encodedCursor == null || encodedCursor.isBlank()
                ? null
                : ProductCursorCodec.decode(encodedCursor);
        List<ProductDocument> documents = queryRepository.findPage(cursor, limit + 1);
        boolean hasMore = documents.size() > limit;
        List<Product> items = documents.stream().limit(limit).map(ProductService::toDomain).toList();
        String nextCursor = hasMore ? ProductCursorCodec.encode(items.getLast()) : null;
        return new ProductPage(items, nextCursor);
    }

    private static Product toDomain(ProductDocument document) {
        return new Product(
                document.getId(),
                document.getSku(),
                document.getName(),
                document.getDescription(),
                document.getAmount(),
                document.getCurrency(),
                document.getStatus(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getVersion());
    }
}
