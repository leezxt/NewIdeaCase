package com.newideacase.platform.shared.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.newideacase.platform.catalog.api.ProductController;
import com.newideacase.platform.catalog.application.ProductService;
import com.newideacase.platform.catalog.domain.Product;
import com.newideacase.platform.catalog.domain.ProductStatus;
import com.newideacase.platform.knowledge.api.KnowledgeDocumentController;
import com.newideacase.platform.knowledge.api.RagController;
import com.newideacase.platform.knowledge.application.KnowledgeAnswerService;
import com.newideacase.platform.knowledge.application.KnowledgeDocumentService;
import com.newideacase.platform.identity.api.UserProfileController;
import com.newideacase.platform.identity.application.UserProfile;
import com.newideacase.platform.identity.application.UserProfileService;
import com.newideacase.platform.ordering.api.OrderController;
import com.newideacase.platform.ordering.application.OrderService;
import com.newideacase.platform.ordering.domain.Order;
import com.newideacase.platform.ordering.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
        ProductController.class,
        KnowledgeDocumentController.class,
        RagController.class,
        UserProfileController.class,
        OrderController.class
})
@Import({
        SecurityConfig.class,
        ProductController.class,
        KnowledgeDocumentController.class,
        RagController.class,
        UserProfileController.class,
        OrderController.class
})
@ImportAutoConfiguration({
        SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@TestPropertySource(properties = {
        "app.security.enabled=true",
        "app.security.issuer-uri=https://issuer.example",
        "app.security.audience=test-api"
})
class SecurityAuthorizationTest {

    @SpringBootConfiguration(proxyBeanMethods = false)
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private KnowledgeDocumentService knowledgeDocumentService;

    @MockitoBean
    private KnowledgeAnswerService knowledgeAnswerService;

    @MockitoBean
    private UserProfileService userProfileService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private RequestIdentityProvider requestIdentityProvider;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void returns401WithoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/products/product-id"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returns403WhenJwtLacksRequiredScope() throws Exception {
        mockMvc.perform(get("/api/v1/products/product-id")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_knowledge.read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsCatalogReadScope() throws Exception {
        when(productService.get("product-id")).thenReturn(product());

        mockMvc.perform(get("/api/v1/products/product-id")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalog.read"))))
                .andExpect(status().isOk());
    }

    @Test
    void separatesCatalogWriteFromReadScope() throws Exception {
        when(productService.create(any())).thenReturn(product());
        String body = """
                {"sku":"SKU-SEC", "name":"Secured", "amount":100, "currency":"TWD"}
                """;

        mockMvc.perform(post("/api/v1/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalog.read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalog.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void requiresKnowledgeWriteScopeForDocumentIntake() throws Exception {
        String body = """
                {"title":"Policy", "mediaType":"text/plain", "content":"secured content"}
                """;

        mockMvc.perform(post("/api/v1/knowledge/documents")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_knowledge.read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void separatesProfileReadScope() throws Exception {
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
        when(userProfileService.getCurrent()).thenReturn(new UserProfile(
                "profile-id", "user-1", "Tester", "zh-TW", "Asia/Taipei",
                now, now, 0L));

        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalog.read"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_profile.read"))))
                .andExpect(status().isOk());
    }

    @Test
    void separatesOrderReadScope() throws Exception {
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
        when(orderService.get("order-id")).thenReturn(new Order(
                "order-id", List.of(), BigDecimal.ZERO, "TWD",
                OrderStatus.PENDING, now, now, 0L));

        mockMvc.perform(get("/api/v1/orders/order-id")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalog.read"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders/order-id")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders.read"))))
                .andExpect(status().isOk());
    }

    private static Product product() {
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        return new Product(
                "product-id", "SKU-SEC", "Secured", null, new BigDecimal("100.00"), "TWD",
                ProductStatus.ACTIVE, now, now, 0L);
    }
}
