package com.autosecretary.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalTime;
import java.time.DayOfWeek;

/**
 * Persists per-day scheduling window preferences (earliest start and latest end time).
 *
 * <p>The task scheduler uses these times to restrict when slots can be placed on each day
 * of the week. For example, the user might set Monday to 08:00–20:00 but Friday to
 * 09:00–18:00. If no preference has been saved for a day, sensible defaults apply
 * (06:00 start, 21:00 end).</p>
 *
 * <h2>Storage</h2>
 * Values are written by {@link com.autosecretary.app.settings.SettingsController} (via the
 * settings menu) and read by the task ViewModel/use-cases. They are stored in the
 * {@code "user_prefs"} SharedPreferences file.
 *
 * <h2>Key format</h2>
 * Keys follow the pattern {@code <DAY>_start} / {@code <DAY>_end}, where {@code <DAY>} is the
 * upper-case English name of the day as returned by {@link DayOfWeek#toString()} —
 * for example {@code MONDAY_start}, {@code FRIDAY_end}.
 *
 * <h2>Placement note</h2>
 * This class lives in {@code app/} because it is a SharedPreferences wrapper, a conventional
 * location for such helpers in Android projects. The scheduling concern is in
 * {@code features/task/}.
 */
public class Preferences {
    private static final String DEFAULT_DAY_START = "06:00";
    private static final String DEFAULT_DAY_END = "21:00";

    /** SharedPreferences file name shared across all settings stored at the app level. */
    private static final String PREFS_FILE = "user_prefs";

    private final SharedPreferences prefs;

    public Preferences(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    }

    public LocalTime readDayStartTime(DayOfWeek day) {
        return LocalTime.parse(prefs.getString(day + "_start", DEFAULT_DAY_START));
    }

    public LocalTime readDayEndTime(DayOfWeek day) {
        return LocalTime.parse(prefs.getString(day + "_end", DEFAULT_DAY_END));
    }
}
