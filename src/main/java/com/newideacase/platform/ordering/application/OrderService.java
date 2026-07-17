package com.newideacase.platform.ordering.application;

import com.newideacase.platform.catalog.application.ProductService;
import com.newideacase.platform.catalog.domain.Product;
import com.newideacase.platform.catalog.domain.ProductStatus;
import com.newideacase.platform.ordering.domain.Order;
import com.newideacase.platform.ordering.domain.OrderStatus;
import com.newideacase.platform.ordering.infrastructure.MongoOrderRepository;
import com.newideacase.platform.ordering.infrastructure.OrderDocument;
import com.newideacase.platform.ordering.infrastructure.OrderLineDocument;
import com.newideacase.platform.shared.error.BadRequestException;
import com.newideacase.platform.shared.error.ConflictException;
import com.newideacase.platform.shared.error.ResourceNotFoundException;
import com.newideacase.platform.shared.security.RequestIdentity;
import com.newideacase.platform.shared.security.RequestIdentityProvider;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final MongoOrderRepository repository;
    private final ProductService productService;
    private final RequestIdentityProvider identityProvider;

    public OrderService(
            MongoOrderRepository repository,
            ProductService productService,
            RequestIdentityProvider identityProvider) {
        this.repository = repository;
        this.productService = productService;
        this.identityProvider = identityProvider;
    }

    public Order create(List<CreateOrderItem> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new BadRequestException("At least one order item is required");
        }
        Set<String> productIds = new HashSet<>();
        requestedItems.forEach(item -> {
            if (!productIds.add(item.productId())) {
                throw new BadRequestException("Duplicate product in order: " + item.productId());
            }
        });
        List<Product> products = requestedItems.stream().map(item -> {
            Product product = productService.get(item.productId());
            if (product.status() != ProductStatus.ACTIVE) {
                throw new BadRequestException("Inactive product cannot be ordered: " + item.productId());
            }
            return product;
        }).toList();
        String currency = products.getFirst().currency();
        if (products.stream().anyMatch(product -> !currency.equals(product.currency()))) {
            throw new BadRequestException("All order items must use the same currency");
        }

        List<OrderLineDocument> lines = java.util.stream.IntStream.range(0, requestedItems.size())
                .mapToObj(index -> {
                    CreateOrderItem item = requestedItems.get(index);
                    Product product = products.get(index);
                    BigDecimal lineTotal = product.amount().multiply(BigDecimal.valueOf(item.quantity()));
                    return new OrderLineDocument(
                            product.id(), product.sku(), product.name(),
                            product.amount(), item.quantity(), lineTotal);
                })
                .toList();
        BigDecimal total = lines.stream()
                .map(OrderLineDocument::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        RequestIdentity identity = identityProvider.current();
        return repository.save(new OrderDocument(
                        identity.tenantId(), identity.subject(), lines, total, currency))
                .toDomain();
    }

    public Order get(String id) {
        RequestIdentity identity = identityProvider.current();
        return findOwned(id, identity).toDomain();
    }

    public Order updateStatus(String id, OrderStatus status, Long version) {
        if (version == null) {
            throw new BadRequestException("Order version is required");
        }
        if (status == null || status == OrderStatus.PENDING) {
            throw new BadRequestException("Order status must be CONFIRMED or CANCELLED");
        }
        RequestIdentity identity = identityProvider.current();
        OrderDocument document = findOwned(id, identity);
        if (!Objects.equals(document.getVersion(), version)) {
            throw new ConflictException("Order was modified by another request");
        }
        document.transitionTo(status);
        try {
            return repository.save(document).toDomain();
        } catch (OptimisticLockingFailureException exception) {
            throw new ConflictException("Order was modified by another request", exception);
        }
    }

    private OrderDocument findOwned(String id, RequestIdentity identity) {
        return repository.findByIdAndTenantIdAndSubject(id, identity.tenantId(), identity.subject())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }
}
