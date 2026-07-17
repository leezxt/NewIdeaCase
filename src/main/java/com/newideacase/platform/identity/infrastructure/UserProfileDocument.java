package com.newideacase.platform.identity.infrastructure;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("user_profiles")
@CompoundIndex(
        name = "tenant_subject_unique_idx",
        def = "{'tenantId': 1, 'subject': 1}",
        unique = true)
public class UserProfileDocument {

    @Id
    private String id;
    private String tenantId;
    private String subject;
    private String displayName;
    private String locale;
    private String timeZone;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    protected UserProfileDocument() {
    }

    public UserProfileDocument(String tenantId, String subject) {
        this.tenantId = tenantId;
        this.subject = subject;
    }

    public void update(String displayName, String locale, String timeZone) {
        this.displayName = displayName;
        this.locale = locale;
        this.timeZone = timeZone;
    }

    public String getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLocale() {
        return locale;
    }

    public String getTimeZone() {
        return timeZone;
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
