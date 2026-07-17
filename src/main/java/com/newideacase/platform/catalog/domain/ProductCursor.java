package com.newideacase.platform.catalog.domain;

import java.time.Instant;

public record ProductCursor(Instant createdAt, String id) {
}
