package com.example.eventsbooking.user;

public record ProfileResponse(
        Long id,
        String email,
        Role role,
        String name,
        String phone,
        String language,
        String city,
        boolean emailVerified) {

    static ProfileResponse from(User user) {
        return new ProfileResponse(user.getId(), user.getEmail(), user.getRole(), user.getName(), user.getPhone(),
                user.getLanguage(), user.getCity(), user.isEmailVerified());
    }
}
