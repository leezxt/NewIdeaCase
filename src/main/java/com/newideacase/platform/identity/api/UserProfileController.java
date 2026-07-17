package com.newideacase.platform.identity.api;

import com.newideacase.platform.identity.application.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserProfileController {

    private final UserProfileService service;

    public UserProfileController(UserProfileService service) {
        this.service = service;
    }

    @GetMapping
    UserProfileResponse get() {
        return UserProfileResponse.from(service.getCurrent());
    }

    @PutMapping
    UserProfileResponse update(@Valid @RequestBody UpdateUserProfileRequest request) {
        return UserProfileResponse.from(service.upsertCurrent(
                request.displayName(), request.locale(), request.timeZone()));
    }
}
