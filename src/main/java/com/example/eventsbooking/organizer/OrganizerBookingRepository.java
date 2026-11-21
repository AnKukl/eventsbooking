package com.example.eventsbooking.organizer;

import com.example.eventsbooking.booking.BookingStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
class OrganizerBookingRepository {

    private final JdbcClient jdbc;

    OrganizerBookingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Newest first. Only the columns the organizer may see are selected (no email, no phone). */
    List<OrganizerBookingResponse> findByEvent(long eventId, int limit, long offset) {
        return jdbc.sql("""
                SELECT b.id, b.status, b.seats, b.created_at, b.hold_expires_at, b.cancelled_at, b.user_id, u.name
                FROM bookings b LEFT JOIN users u ON u.id = b.user_id
                WHERE b.event_id = :eventId
                ORDER BY b.created_at DESC, b.id DESC
                LIMIT :limit OFFSET :offset
                """)
                .param("eventId", eventId).param("limit", limit).param("offset", offset)
                .query((rs, row) -> new OrganizerBookingResponse(rs.getLong("id"),
                        BookingStatus.valueOf(rs.getString("status")), rs.getInt("seats"),
                        instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("hold_expires_at")),
                        instant(rs.getTimestamp("cancelled_at")),
                        new OrganizerBookingResponse.Attendee(rs.getLong("user_id"), rs.getString("name"))))
                .list();
    }

    long countByEvent(long eventId) {
        return jdbc.sql("SELECT count(*) FROM bookings WHERE event_id = :eventId")
                .param("eventId", eventId).query(Long.class).single();
    }

    private static java.time.Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
