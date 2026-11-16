package com.example.eventsbooking.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

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

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private long organizerId;

    protected Event() {
        // for JPA
    }

    public Event(String title, String description, Instant startsAt, int capacity, long organizerId) {
        update(title, description, startsAt, capacity, organizerId);
    }

    public void update(String title, String description, Instant startsAt, int capacity, long organizerId) {
        this.title = title;
        this.description = description;
        this.startsAt = startsAt;
        this.capacity = capacity;
        this.organizerId = organizerId;
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

    public Instant getStartsAt() {
        return startsAt;
    }

    public int getCapacity() {
        return capacity;
    }

    public long getOrganizerId() {
        return organizerId;
    }
}
