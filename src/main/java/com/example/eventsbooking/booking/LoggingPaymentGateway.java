package com.example.eventsbooking.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Placeholder until the payment stage: records that a refund was requested; moves no money. */
@Component
class LoggingPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingPaymentGateway.class);

    @Override
    public void refund(Booking booking) {
        log.info("Refund requested for booking {} (stub gateway: nothing was refunded)", booking.getId());
    }
}
