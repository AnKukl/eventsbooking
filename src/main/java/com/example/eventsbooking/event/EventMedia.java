package com.example.eventsbooking.event;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One image of an event; its position in the list is stored by the order column (0 is the cover). */
@Embeddable
public record EventMedia(
        @Column(name = "url", nullable = false, length = 2048) String url,
        @Column(name = "alt", length = 200) String alt) {
}
