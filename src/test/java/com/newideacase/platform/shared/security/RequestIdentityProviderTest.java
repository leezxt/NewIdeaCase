package com.newideacase.platform.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class RequestIdentityProviderTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsTenantAndRolesFromBackendJwtClaims() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-1")
                .claim("tenant_id", "tenant-a")
                .claim("roles", List.of("support", "catalog-admin"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        RequestIdentityProvider provider = new RequestIdentityProvider(
                new SecurityProperties(true, "https://issuer.example", "api", "tenant_id", "roles"),
                "local");

        RequestIdentity identity = provider.current();

        assertThat(identity.tenantId()).isEqualTo("tenant-a");
        assertThat(identity.roles()).containsExactlyInAnyOrder("support", "catalog-admin");
    }

    @Test
    void rejectsAuthenticatedJwtWithoutTenantClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-1")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        RequestIdentityProvider provider = new RequestIdentityProvider(
                new SecurityProperties(true, "https://issuer.example", "api", "tenant_id", "roles"),
                "local");

        assertThatThrownBy(provider::current).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsAuthenticatedJwtWithoutSubjectClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("tenant_id", "tenant-a")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        RequestIdentityProvider provider = new RequestIdentityProvider(
                new SecurityProperties(true, "https://issuer.example", "api", "tenant_id", "roles"),
                "local");

        assertThatThrownBy(provider::current)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("JWT subject claim is required");
    }

    @Test
    void usesConfiguredTenantWhenSecurityIsDisabled() {
        RequestIdentityProvider provider = new RequestIdentityProvider(
                new SecurityProperties(false, null, null, null, null), "test-local");

        assertThat(provider.current()).isEqualTo(new RequestIdentity("test-local", java.util.Set.of()));
    }
}
