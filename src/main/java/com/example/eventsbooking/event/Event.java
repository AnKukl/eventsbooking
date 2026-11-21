package com.example.eventsbooking.event;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.example.eventsbooking.common.ConflictException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 160)
    private String shortDescription = "";

    // Preview lists. Lazy and never touched by the list endpoints; read inside a transaction only (preview).
    @ElementCollection
    @CollectionTable(name = "event_audience_fit", joinColumns = @JoinColumn(name = "event_id"))
    @OrderColumn(name = "position")
    @Column(name = "label", nullable = false, length = 100)
    private List<String> audienceFit = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "event_audience_not_fit", joinColumns = @JoinColumn(name = "event_id"))
    @OrderColumn(name = "position")
    @Column(name = "label", nullable = false, length = 100)
    private List<String> audienceNotFit = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "event_media", joinColumns = @JoinColumn(name = "event_id"))
    @OrderColumn(name = "position")
    private List<EventMedia> media = new ArrayList<>();

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private long organizerId;

    @Column(nullable = false)
    private int bookedCount;

    /**
     * Версия для оптимистической блокировки. Hibernate добавляет {@code AND version = ?} в каждый UPDATE
     * и увеличивает версию, поэтому две параллельные транзакции не могут обе записать изменение
     * (например, занять последнее место), основанное на одном и том же прочитанном состоянии.
     */
    @Version
    private long version;

    /** Events created through the public API are published at once; the organizer cabinet creates drafts. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EventStatus status = EventStatus.PUBLISHED;

    @Column(length = 200)
    private String venue;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean waitlistEnabled = true;

    /**
     * The version an organizer edits against. Unlike {@link #version} it does not change when somebody books, so an
     * open edit form is not made stale by bookings; it changes only when an organizer-side edit is saved.
     */
    @Column(nullable = false)
    private long revision;

    protected Event() {
        // for JPA
    }

    public Event(String title, String description, Instant startsAt, int capacity, long organizerId) {
        update(title, description, startsAt, capacity, organizerId);
    }

    /** Takes one seat. */
    public void book() {
        book(1);
    }

    /**
     * Takes {@code seats} seats at once or none. This changes the event, and with it its version: that is the point of
     * contention between concurrent bookings.
     */
    public void book(int seats) {
        if (seats < 1 || bookedCount + seats > capacity) {
            throw new EventFullException(id);
        }
        bookedCount += seats;
    }

    /** Gives one seat back. */
    public void release() {
        release(1);
    }

    /** Gives {@code seats} seats back (a cancellation or an expired hold); bumps the version like {@link #book(int)}. */
    public void release(int seats) {
        if (seats < 1 || bookedCount < seats) {
            throw new IllegalStateException("Event " + id + " has no " + seats + " booked seats to release");
        }
        bookedCount -= seats;
    }

    public void update(String title, String description, Instant startsAt, int capacity, long organizerId) {
        if (capacity < bookedCount) {
            throw new ConflictException("Capacity below bookings",
                    "Capacity " + capacity + " is less than the " + bookedCount + " seats already booked");
        }
        this.title = title;
        this.description = description;
        this.startsAt = startsAt;
        this.capacity = capacity;
        this.organizerId = organizerId;
    }

    /** A draft: visible only to its organizer until it is published. */
    public static Event draft(String title, String description, Instant startsAt, int capacity, long organizerId) {
        Event event = new Event(title, description, startsAt, capacity, organizerId);
        event.status = EventStatus.DRAFT;
        return event;
    }

    /** Organizer edit; {@code null} keeps the current value. Saving it is what bumps the revision (see the service). */
    public void organizerUpdate(String title, String description, Instant startsAt, Integer capacity, String venue,
            BigDecimal price, Boolean waitlistEnabled) {
        update(title != null ? title : this.title, description != null ? description : this.description,
                startsAt != null ? startsAt : this.startsAt, capacity != null ? capacity : this.capacity,
                this.organizerId);
        if (venue != null) {
            this.venue = venue.isBlank() ? null : venue.strip();
        }
        if (price != null) {
            this.price = price;
        }
        if (waitlistEnabled != null) {
            this.waitlistEnabled = waitlistEnabled;
        }
    }

    public void bumpRevision() {
        revision++;
    }

    /** What still has to be filled in before this event may be published (empty: ready). */
    public List<MissingField> missingForPublish(Instant now) {
        List<MissingField> missing = new ArrayList<>();
        if (title == null || title.isBlank()) {
            missing.add(new MissingField("title", "must not be blank"));
        }
        if (startsAt == null || !startsAt.isAfter(now)) {
            missing.add(new MissingField("startsAt", "must be in the future"));
        }
        if (venue == null || venue.isBlank()) {
            missing.add(new MissingField("venue", "must be set"));
        }
        if (price == null) {
            missing.add(new MissingField("price", "must be set (0 for a free event)"));
        }
        if (media.isEmpty()) {
            missing.add(new MissingField("media", "at least one photo is required"));
        }
        return missing;
    }

    public void publish() {
        this.status = EventStatus.PUBLISHED;
    }

    /** Appends images after the existing ones (the first image of the event stays the cover). */
    public void addMedia(List<EventMedia> more) {
        media.addAll(more);
    }

    /**
     * Sets the preview fields. {@code null} keeps the current value; an empty list or string clears it. Limits are
     * validated at the API boundary and backed by database constraints.
     */
    public void applyPreview(String shortDescription, List<String> audienceFit, List<String> audienceNotFit,
            List<EventMedia> media) {
        if (shortDescription != null) {
            this.shortDescription = shortDescription;
        }
        replace(this.audienceFit, audienceFit);
        replace(this.audienceNotFit, audienceNotFit);
        replace(this.media, media);
    }

    private static <T> void replace(List<T> current, List<T> replacement) {
        if (replacement != null) {
            current.clear();
            current.addAll(replacement);
        }
    }

    /** Seats left or not; derived from the counters, never stored. */
    public SeatStatus seatStatus() {
        return bookedCount >= capacity ? SeatStatus.SOLD_OUT : SeatStatus.AVAILABLE;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public EventStatus getStatus() {
        return status;
    }

    public String getVenue() {
        return venue;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isWaitlistEnabled() {
        return waitlistEnabled;
    }

    public long getRevision() {
        return revision;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public List<String> getAudienceFit() {
        return List.copyOf(audienceFit);
    }

    public List<String> getAudienceNotFit() {
        return List.copyOf(audienceNotFit);
    }

    public List<EventMedia> getMedia() {
        return List.copyOf(media);
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public int getCapacity() {
        return capacity;
    }

    public long getOrganizerId() {
        return organizerId;
    }

    public int getBookedCount() {
        return bookedCount;
    }
}
