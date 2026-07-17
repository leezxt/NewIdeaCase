package com.newideacase.platform.identity.application;

import java.time.Instant;

public record UserProfile(
        String id,
        String subject,
        String displayName,
        String locale,
        String timeZone,
        Instant createdAt,
        Instant updatedAt,
        Long version) {
}
