package com.newideacase.platform.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @NotBlank @Size(max = 100) String displayName,
        @Pattern(regexp = "[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})?") String locale,
        @Size(max = 100) String timeZone) {
}
