package com.example.eventsbooking.booking;

/**
 * Money side of a booking. Only refunds exist for now; the real implementation arrives with the payment stage.
 * A refund request must be idempotent per booking: it may be repeated until the booking leaves REFUND_PENDING.
 */
public interface PaymentGateway {

    void refund(Booking booking);
}
