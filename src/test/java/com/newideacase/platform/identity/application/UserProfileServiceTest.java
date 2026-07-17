package com.newideacase.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.newideacase.platform.identity.infrastructure.MongoUserProfileRepository;
import com.newideacase.platform.identity.infrastructure.UserProfileDocument;
import com.newideacase.platform.shared.error.BadRequestException;
import com.newideacase.platform.shared.security.RequestIdentity;
import com.newideacase.platform.shared.security.RequestIdentityProvider;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private MongoUserProfileRepository repository;

    @Mock
    private RequestIdentityProvider identityProvider;

    private UserProfileService service;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(repository, identityProvider);
        when(identityProvider.current()).thenReturn(
                new RequestIdentity("user-1", "tenant-1", Set.of("support")));
    }

    @Test
    void createsProfileForBackendIdentity() {
        when(repository.findByTenantIdAndSubject("tenant-1", "user-1"))
                .thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(UserProfileDocument.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        UserProfile profile = service.upsertCurrent(" API Tester ", "zh-TW", "Asia/Taipei");

        assertThat(profile.subject()).isEqualTo("user-1");
        assertThat(profile.displayName()).isEqualTo("API Tester");
        assertThat(profile.timeZone()).isEqualTo("Asia/Taipei");
    }

    @Test
    void rejectsUnknownTimeZone() {
        assertThatThrownBy(() -> service.upsertCurrent("Tester", "zh-TW", "Mars/Olympus"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("time zone");
    }

    private static UserProfileDocument persisted(UserProfileDocument document) {
        ReflectionTestUtils.setField(document, "id", "profile-id");
        ReflectionTestUtils.setField(document, "createdAt", Instant.parse("2026-07-17T00:00:00Z"));
        ReflectionTestUtils.setField(document, "updatedAt", Instant.parse("2026-07-17T00:00:00Z"));
        ReflectionTestUtils.setField(document, "version", 0L);
        return document;
    }
}
