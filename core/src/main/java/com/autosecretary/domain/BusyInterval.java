package com.autosecretary.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/** A calendar occurrence after the explicit blocking policy has been applied. */
public record BusyInterval(
        String id,
        LocalDateTime start,
        LocalDateTime end,
        String title,
        TitleVisibility titleVisibility) {
    public enum TitleVisibility { VISIBLE, HIDDEN }

    public BusyInterval(LocalDateTime start, LocalDateTime end, String title) {
        this("legacy:" + Integer.toHexString(Objects.hash(start, end, title)),
                start, end, title == null ? "Termin" : title, TitleVisibility.VISIBLE);
    }

    public BusyInterval {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Kalender-ID fehlt");
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Ungültiger Kalenderblock");
        }
        if (titleVisibility == null) titleVisibility = TitleVisibility.VISIBLE;
        if (titleVisibility == TitleVisibility.HIDDEN) title = null;
        else if (title == null || title.isBlank()) title = "Termin";
    }
}
