package com.newideacase.platform.ordering.infrastructure;

import com.newideacase.platform.ordering.domain.Order;
import com.newideacase.platform.ordering.domain.OrderStatus;
import com.newideacase.platform.shared.error.ConflictException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("orders")
@CompoundIndex(
        name = "tenant_subject_created_idx",
        def = "{'tenantId': 1, 'subject': 1, 'createdAt': -1}")
public class OrderDocument {

    @Id
    private String id;
    private String tenantId;
    private String subject;
    private List<OrderLineDocument> items;
    private BigDecimal totalAmount;
    private String currency;
    private OrderStatus status;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    protected OrderDocument() {
    }

    public OrderDocument(
            String tenantId,
            String subject,
            List<OrderLineDocument> items,
            BigDecimal totalAmount,
            String currency) {
        this.tenantId = tenantId;
        this.subject = subject;
        this.items = List.copyOf(items);
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.status = OrderStatus.PENDING;
    }

    public void transitionTo(OrderStatus target) {
        if (status != OrderStatus.PENDING) {
            throw new ConflictException("Only a pending order can change status");
        }
        status = target;
    }

    public Order toDomain() {
        return new Order(
                id,
                items.stream().map(OrderLineDocument::toDomain).toList(),
                totalAmount,
                currency,
                status,
                createdAt,
                updatedAt,
                version);
    }

    public Long getVersion() {
        return version;
    }
}
