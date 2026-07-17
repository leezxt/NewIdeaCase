package com.newideacase.platform.identity.application;

import com.newideacase.platform.identity.infrastructure.MongoUserProfileRepository;
import com.newideacase.platform.identity.infrastructure.UserProfileDocument;
import com.newideacase.platform.shared.error.BadRequestException;
import com.newideacase.platform.shared.error.ResourceNotFoundException;
import com.newideacase.platform.shared.security.RequestIdentity;
import com.newideacase.platform.shared.security.RequestIdentityProvider;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

    private final MongoUserProfileRepository repository;
    private final RequestIdentityProvider identityProvider;

    public UserProfileService(
            MongoUserProfileRepository repository,
            RequestIdentityProvider identityProvider) {
        this.repository = repository;
        this.identityProvider = identityProvider;
    }

    public UserProfile getCurrent() {
        RequestIdentity identity = identityProvider.current();
        return repository.findByTenantIdAndSubject(identity.tenantId(), identity.subject())
                .map(UserProfileService::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("User profile has not been created"));
    }

    public UserProfile upsertCurrent(String displayName, String locale, String timeZone) {
        RequestIdentity identity = identityProvider.current();
        validateTimeZone(timeZone);
        UserProfileDocument document = repository
                .findByTenantIdAndSubject(identity.tenantId(), identity.subject())
                .orElseGet(() -> new UserProfileDocument(identity.tenantId(), identity.subject()));
        document.update(displayName.trim(), normalizeOptional(locale), normalizeOptional(timeZone));
        return toDomain(repository.save(document));
    }

    private static void validateTimeZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return;
        }
        try {
            ZoneId.of(timeZone.trim());
        } catch (ZoneRulesException exception) {
            throw new BadRequestException("Unknown time zone: " + timeZone, exception);
        }
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static UserProfile toDomain(UserProfileDocument document) {
        return new UserProfile(
                document.getId(),
                document.getSubject(),
                document.getDisplayName(),
                document.getLocale(),
                document.getTimeZone(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getVersion());
    }
}
