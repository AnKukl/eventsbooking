package com.example.eventsbooking.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Event> {

    /** The public catalog: only published events. */
    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    boolean existsByIdAndStatus(long id, EventStatus status);

    // ---- the organizer's own events ----

    Page<Event> findByOrganizerId(long organizerId, Pageable pageable);

    Page<Event> findByOrganizerIdAndStatus(long organizerId, EventStatus status, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.organizerId = :organizerId AND e.status = com.example.eventsbooking.event.EventStatus.PUBLISHED "
            + "AND e.bookedCount < e.capacity")
    Page<Event> findPublishedWithFreeSeats(@Param("organizerId") long organizerId, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.organizerId = :organizerId AND e.status = com.example.eventsbooking.event.EventStatus.PUBLISHED "
            + "AND e.bookedCount >= e.capacity")
    Page<Event> findSoldOut(@Param("organizerId") long organizerId, Pageable pageable);
}
