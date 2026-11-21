package com.example.eventsbooking.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A pending email change. Holds the hash of the token, never the token itself. */
@Entity
@Table(name = "email_verifications")
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long userId;

    @Column(nullable = false, length = 255)
    private String newEmail;

    @Column(nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    protected EmailVerification() {
        // for JPA
    }

    public EmailVerification(long userId, String newEmail, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.newEmail = newEmail;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public long getUserId() {
        return userId;
    }

    public String getNewEmail() {
        return newEmail;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public String toString() {
        return "EmailVerification[userId=" + userId + "]";   // no hash, no address
    }
}
