package com.example.eventsbooking.booking;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    org.springframework.data.domain.Page<Booking> findByUserId(long userId, org.springframework.data.domain.Pageable pageable);

    boolean existsByEventIdAndUserIdAndStatus(long eventId, long userId, BookingStatus status);

    long countByEventId(long eventId);
}
