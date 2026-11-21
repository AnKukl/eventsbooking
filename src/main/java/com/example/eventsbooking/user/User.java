package com.example.eventsbooking.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(length = 100)
    private String name;

    @Column(length = 16)
    private String phone;

    @Column(nullable = false, length = 2)
    private String language = "EN";

    @Column(length = 100)
    private String city;

    @Column(nullable = false)
    private boolean emailVerified;

    protected User() {
        // for JPA
    }

    public User(String email, String passwordHash, Role role, Instant createdAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    /** {@code null} keeps the current value; an empty string clears name, phone and city. */
    public void updateProfile(String name, String phone, String language, String city) {
        if (name != null) {
            this.name = blankToNull(name);
        }
        if (phone != null) {
            this.phone = blankToNull(phone);
        }
        if (language != null) {
            this.language = language;
        }
        if (city != null) {
            this.city = blankToNull(city);
        }
    }

    /** Only called after the address was confirmed through a token sent to it. */
    public void changeEmail(String confirmedEmail) {
        this.email = confirmedEmail;
        this.emailVerified = true;
    }

    private static String blankToNull(String value) {
        return value.isBlank() ? null : value.strip();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getLanguage() {
        return language;
    }

    public String getCity() {
        return city;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
