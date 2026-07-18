package com.newideacase.platform.shared.security;

import static org.springframework.security.config.Customizer.withDefaults;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain localSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true")
    SecurityFilterChain oauthSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(
                                "/actuator/health/**", "/actuator/prometheus",
                                "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
                                "/dashboard", "/dashboard/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**")
                        .hasAuthority("SCOPE_catalog.read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products")
                        .hasAuthority("SCOPE_catalog.write")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/products/**")
                        .hasAuthority("SCOPE_catalog.write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/knowledge/documents/**")
                        .hasAuthority("SCOPE_knowledge.read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/knowledge/documents")
                        .hasAuthority("SCOPE_knowledge.write")
                        .requestMatchers(HttpMethod.POST, "/api/v1/rag/answers")
                        .hasAuthority("SCOPE_knowledge.read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me")
                        .hasAuthority("SCOPE_profile.read")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/me")
                        .hasAuthority("SCOPE_profile.write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/**")
                        .hasAuthority("SCOPE_orders.read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders")
                        .hasAuthority("SCOPE_orders.write")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/orders/**")
                        .hasAuthority("SCOPE_orders.write")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(withDefaults()))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true")
    JwtDecoder jwtDecoder(SecurityProperties properties) {
        if (!StringUtils.hasText(properties.issuerUri()) || !StringUtils.hasText(properties.audience())) {
            throw new IllegalStateException("OIDC issuer URI and audience are required when security is enabled");
        }
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(properties.issuerUri());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefaultWithIssuer(properties.issuerUri()),
                new AudienceValidator(properties.audience())));
        return decoder;
    }
}
