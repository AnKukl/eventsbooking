package com.example.eventsbooking.user;

import com.example.eventsbooking.common.ConflictException;

public class EmailAlreadyRegisteredException extends ConflictException {

    public EmailAlreadyRegisteredException() {
        super("Email already registered", "A user with this email is already registered");
    }
}
