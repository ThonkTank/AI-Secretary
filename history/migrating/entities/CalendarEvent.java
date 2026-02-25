package entities;

import java.time.LocalTime;

/** Kalender-Event mit Titel und Zeitfenster. Keine Android-Abhängigkeit. */
public record CalendarEvent(String title, LocalTime start, LocalTime end) {}
