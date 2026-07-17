package com.newideacase.platform.shared.security;

import java.util.Collections;
import java.util.Set;

public record RequestIdentity(String subject, String tenantId, Set<String> roles) {

    public RequestIdentity {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID is required");
        }
        subject = subject.trim();
        tenantId = tenantId.trim();
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public RequestIdentity(String tenantId, Set<String> roles) {
        this("local-user", tenantId, roles);
    }

    public boolean canAccess(Set<String> allowedRoles) {
        return allowedRoles == null
                || allowedRoles.isEmpty()
                || !Collections.disjoint(roles, allowedRoles);
    }
}
