package com.example.eventsbooking.event;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.example.eventsbooking.common.ConflictException;

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

    protected Event() {
        // for JPA
    }

    public Event(String title, String description, Instant startsAt, int capacity, long organizerId) {
        update(title, description, startsAt, capacity, organizerId);
    }

    /** Занимает одно место. Меняет состояние события, а значит и версию — это и есть точка конкуренции. */
    public void book() {
        if (bookedCount >= capacity) {
            throw new EventFullException(id);
        }
        bookedCount++;
    }

    /** Возвращает место (отмена брони). Так же меняет версию, как и {@link #book()}. */
    public void release() {
        if (bookedCount <= 0) {
            throw new IllegalStateException("Event " + id + " has no booked seats to release");
        }
        bookedCount--;
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
