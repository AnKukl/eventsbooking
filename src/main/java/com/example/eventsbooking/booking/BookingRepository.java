package com.example.eventsbooking.booking;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByEventIdAndUserIdAndStatus(long eventId, long userId, BookingStatus status);

    long countByEventId(long eventId);
}
