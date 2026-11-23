package com.example.eventsbooking.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param secure the {@code Secure} attribute of the session cookies. On by default; switch it off explicitly only for a
 *               local run over plain http (a browser does not send a Secure cookie over http).
 */
@ConfigurationProperties("app.security.cookie")
public record CookieProperties(@DefaultValue("true") boolean secure) {
}
