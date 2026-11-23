package com.example.eventsbooking.user;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param ttl         lifetime of a refresh token; every rotation starts a new one
 * @param reuseGrace  a token rotated less than this long ago and presented again is taken for a race between two tabs
 *                    (401, the session stays); presented later, it is taken for a stolen copy (the family is revoked)
 */
@ConfigurationProperties("app.security.refresh")
public record RefreshProperties(@DefaultValue("P14D") Duration ttl, @DefaultValue("PT10S") Duration reuseGrace) {
}
