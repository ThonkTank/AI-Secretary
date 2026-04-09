package com.autosecretary.shared;

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

    /** Full German date (e.g. "3. März 2026"). Used in budget transaction rows. */
    public static final DateTimeFormatter DATE_FULL_GERMAN =
            DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN);

    /** Short German date (e.g. "03.03.2026"). Used in task deadline display. */
    public static final DateTimeFormatter DATE_SHORT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);

    /** Month label (e.g. "März 2026"). Used in budget month navigation. */
    public static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN);

    /** Daily chart axis label (e.g. "03.03"). */
    public static final DateTimeFormatter CHART_DAILY =
            DateTimeFormatter.ofPattern("dd.MM", Locale.GERMAN);

    /** Monthly chart axis label (e.g. "Mär 26"). */
    public static final DateTimeFormatter CHART_MONTHLY =
            DateTimeFormatter.ofPattern("MMM yy", Locale.GERMAN);

    private DateFormatters() {}
}
