package com.example.eventsbooking.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder until a real mail transport (SMTP) is connected: nothing is delivered. It logs that a message was
 * requested, never the address or the token.
 */
@Component
class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void sendVerification(long userId, String email, String token) {
        log.info("Email confirmation requested for user {} (no mail transport configured, nothing was sent)", userId);
    }
}
