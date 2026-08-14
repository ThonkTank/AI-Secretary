package com.autosecretary.domain;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Immutable child step with identity independent from its position. */
public record Step(String id, String title, Set<DayOfWeek> days, int position) {
    public Step {
        id = requireId(id);
        title = requireTitle(title);
        if (position < 0) throw new IllegalArgumentException("Schrittposition darf nicht negativ sein");
        days = days == null || days.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(days));
    }

    public static Step create(String title, Set<DayOfWeek> days, int position) {
        return new Step(UUID.randomUUID().toString(), title, days, position);
    }

    public boolean appliesOn(DayOfWeek day) {
        return days.isEmpty() || days.contains(day);
    }

    private static String requireId(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException("Schritt-ID fehlt");
        try { UUID.fromString(result); }
        catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Schritt-ID ist keine UUID", error);
        }
        return result;
    }

    private static String requireTitle(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException("Schritttitel fehlt");
        return result;
    }
}
