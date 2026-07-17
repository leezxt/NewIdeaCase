package com.newideacase.platform.catalog.infrastructure;

import com.newideacase.platform.catalog.domain.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("products")
@CompoundIndex(name = "status_updated_at_idx", def = "{'status': 1, 'updatedAt': -1}")
public class ProductDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sku;

    private String name;
    private String description;
    private BigDecimal amount;
    private String currency;
    private ProductStatus status;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    protected ProductDocument() {
    }

    public ProductDocument(
            String sku,
            String name,
            String description,
            BigDecimal amount,
            String currency,
            ProductStatus status) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
    }

    public void update(
            String name,
            String description,
            boolean clearDescription,
            BigDecimal amount,
            String currency,
            ProductStatus status) {
        if (name != null) {
            this.name = name;
        }
        if (clearDescription) {
            this.description = null;
        } else if (description != null) {
            this.description = description;
        }
        if (amount != null) {
            this.amount = amount;
        }
        if (currency != null) {
            this.currency = currency;
        }
        if (status != null) {
            this.status = status;
        }
    }

    public String getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
