package com.newideacase.platform.shared.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RequestIdentityProvider {

    private static final String DEFAULT_TENANT_CLAIM = "tenant_id";
    private static final String DEFAULT_ROLES_CLAIM = "roles";

    private final SecurityProperties securityProperties;
    private final String defaultTenantId;

    public RequestIdentityProvider(
            SecurityProperties securityProperties,
            @Value("${app.knowledge.default-tenant-id:local}") String defaultTenantId) {
        this.securityProperties = securityProperties;
        this.defaultTenantId = defaultTenantId;
    }

    public RequestIdentity current() {
        if (!securityProperties.enabled()) {
            return new RequestIdentity("local-user", defaultTenantId, Set.of());
        }

        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)
                || !token.isAuthenticated()) {
            throw new AccessDeniedException("An authenticated JWT is required");
        }

        String tenantClaim = StringUtils.hasText(securityProperties.tenantClaim())
                ? securityProperties.tenantClaim()
                : DEFAULT_TENANT_CLAIM;
        String tenantId = token.getToken().getClaimAsString(tenantClaim);
        if (!StringUtils.hasText(tenantId)) {
            throw new AccessDeniedException("JWT tenant claim is required");
        }

        String rolesClaim = StringUtils.hasText(securityProperties.rolesClaim())
                ? securityProperties.rolesClaim()
                : DEFAULT_ROLES_CLAIM;
        String subject = token.getToken().getSubject();
        if (!StringUtils.hasText(subject)) {
            throw new AccessDeniedException("JWT subject claim is required");
        }

        return new RequestIdentity(
                subject,
                tenantId,
                extractRoles(token.getToken().getClaim(rolesClaim)));
    }

    private static Set<String> extractRoles(Object claim) {
        if (claim == null) {
            return Set.of();
        }
        Collection<?> values = claim instanceof Collection<?> collection
                ? collection
                : Set.of(claim);
        Set<String> roles = new LinkedHashSet<>();
        values.stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(roles::add);
        return Set.copyOf(roles);
    }
}
