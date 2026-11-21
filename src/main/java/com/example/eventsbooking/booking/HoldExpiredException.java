package com.example.eventsbooking.booking;

import com.example.eventsbooking.common.ConflictException;

public class HoldExpiredException extends ConflictException {

    public HoldExpiredException() {
        super("Hold expired", "The hold on these seats has expired: book again");
    }
}
