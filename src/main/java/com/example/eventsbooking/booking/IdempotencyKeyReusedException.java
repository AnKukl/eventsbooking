package com.example.eventsbooking.booking;

import com.example.eventsbooking.common.ConflictException;

public class IdempotencyKeyReusedException extends ConflictException {

    public IdempotencyKeyReusedException() {
        super("Idempotency key reused", "This Idempotency-Key was already used for a different request");
    }
}
