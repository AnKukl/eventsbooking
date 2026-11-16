package com.example.eventsbooking.booking;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByEventIdAndUserId(long eventId, long userId);

    long countByEventId(long eventId);
}
