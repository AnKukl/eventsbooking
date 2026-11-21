package com.example.eventsbooking.user;

import com.example.eventsbooking.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/** Who am I, and my own profile: lets a frontend show the current user and decide which screens to offer. */
@RestController
@RequestMapping("/api/me")
class MeController {

    private final AuthService authService;
    private final ProfileService profileService;

    MeController(AuthService authService, ProfileService profileService) {
        this.authService = authService;
        this.profileService = profileService;
    }

    @GetMapping
    ProfileResponse me(Principal principal) {
        return ProfileResponse.from(authService.profile(CurrentUser.id(principal)));
    }

    /** Name, phone, language and city. The email is not part of it: it changes only through confirmation. */
    @PatchMapping
    ProfileResponse update(@Valid @RequestBody ProfileUpdateRequest request, Principal principal) {
        return ProfileResponse.from(profileService.updateProfile(CurrentUser.id(principal), request));
    }

    /** Starts an email change (or the verification of the current address); effective only once confirmed. */
    @PostMapping("/email-change")
    ResponseEntity<Void> requestEmailChange(@Valid @RequestBody EmailChangeRequest request, Principal principal) {
        profileService.requestEmailChange(CurrentUser.id(principal), request.newEmail());
        return ResponseEntity.accepted().build();
    }
}
