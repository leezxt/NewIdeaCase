package com.newideacase.platform.identity.api;

import com.newideacase.platform.identity.application.UserProfile;
import java.time.Instant;

public record UserProfileResponse(
        String id,
        String userId,
        String displayName,
        String locale,
        String timeZone,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    static UserProfileResponse from(UserProfile profile) {
        return new UserProfileResponse(
                profile.id(),
                profile.subject(),
                profile.displayName(),
                profile.locale(),
                profile.timeZone(),
                profile.createdAt(),
                profile.updatedAt(),
                profile.version());
    }
}
