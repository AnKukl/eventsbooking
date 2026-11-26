package com.example.eventsbooking.event;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the public catalogue may be narrowed by: a time window [from, to), words in the title, the short description or
 * the venue ({@code q}), a price ceiling and a city. There is no separate city field: an event's city is part of its
 * venue ("Grand Cafe, Sopot"), so {@code city} is matched in the venue; several spellings may be given separated by
 * commas ("Sopot,Сопот") and any of them matches.
 */
public record EventFilter(Instant from, Instant to, String q, BigDecimal maxPrice, String city) {

    public static final EventFilter NONE = new EventFilter(null, null, null, null, null);

    public boolean isEmpty() {
        return from == null && to == null && blank(q) && maxPrice == null && blank(city);
    }

    /** Published events matching every given condition. */
    Specification<Event> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            all.add(cb.equal(root.get("status"), EventStatus.PUBLISHED));
            if (from != null) {
                all.add(cb.greaterThanOrEqualTo(root.get("startsAt"), from));
            }
            if (to != null) {
                all.add(cb.lessThan(root.get("startsAt"), to));
            }
            if (!blank(q)) {
                String pattern = contains(q);
                all.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("shortDescription")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("venue"), "")), pattern, '\\')));
            }
            if (maxPrice != null) {
                all.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            if (!blank(city)) {
                // "Sopot,Сопот": venues spell a city in Polish or Russian, any spelling matches
                List<Predicate> spellings = new ArrayList<>();
                for (String spelling : city.split(",")) {
                    if (!spelling.isBlank()) {
                        spellings.add(cb.like(cb.lower(cb.coalesce(root.get("venue"), "")), contains(spelling), '\\'));
                    }
                }
                all.add(cb.or(spellings.toArray(Predicate[]::new)));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    /** "%text%" in lower case, with the LIKE wildcards of the user's text escaped. */
    private static String contains(String text) {
        String escaped = text.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
