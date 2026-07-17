package com.newideacase.platform.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security")
public record SecurityProperties(
        boolean enabled,
        String issuerUri,
        String audience,
        String tenantClaim,
        String rolesClaim) {
}
