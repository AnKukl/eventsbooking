package com.example.eventsbooking.user;

import com.example.eventsbooking.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/** Who am I: lets a frontend show the current user and decide which screens to offer. */
@RestController
@RequestMapping("/api/me")
class MeController {

    private final AuthService service;

    MeController(AuthService service) {
        this.service = service;
    }

    @GetMapping
    UserResponse me(Principal principal) {
        return UserResponse.from(service.profile(CurrentUser.id(principal)));
    }
}
