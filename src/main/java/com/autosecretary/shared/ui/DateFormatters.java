package com.autosecretary.shared.ui;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Shared date/time formatters used across multiple features.
 * Centralizes commonly duplicated {@link DateTimeFormatter} instances.
 */
public final class DateFormatters {

    /** 24-hour time format (e.g. "14:30"). Used in task rows, widgets, schedule config, slot generator. */
    public static final DateTimeFormatter TIME_HH_MM =
            DateTimeFormatter.ofPattern("HH:mm");

    /** Day navigation label (e.g. "Montag, 3. Mär"). Used in task list and task widget headers. */
    public static final DateTimeFormatter DAY_NAV_LABEL =
            DateTimeFormatter.ofPattern("EEEE, d. MMM", Locale.GERMAN);

    private DateFormatters() {}
}
