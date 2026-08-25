package de.thonktank.autosecretary.data.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.calendar.CalendarPolicy;

public final class UiPreferences {
    public interface Subscription extends AutoCloseable {
        @Override void close();
    }

    private static final String TAG = "UiPreferences";
    private static final String FILE = "forest_ui";
    private static final String THEME_MODE = "theme_mode";
    private static final String CALENDAR_ASKED = "calendar_asked";
    private static final String CALENDAR_POLICY = "calendar_policy";
    private static final String FOCUS_STEP_LIMIT = "focus_step_limit";
    private static final String REST_TIMER_DEFAULT_SECONDS = "rest_timer_default_seconds";
    public static final int DEFAULT_REST_TIMER_SECONDS = 60;

    private final SharedPreferences preferences;
    private final AppLogger logger;

    public UiPreferences(Context context, AppLogger logger) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
        this.logger = logger;
    }

    public UiThemeMode themeMode() {
        String stored = preferences.getString(THEME_MODE, UiThemeMode.AUTO.name());
        try {
            return UiThemeMode.valueOf(stored);
        } catch (IllegalArgumentException error) {
            logger.error(TAG, "Ignoring unsupported stored theme mode: " + stored, error);
            return UiThemeMode.AUTO;
        }
    }

    public void setThemeMode(UiThemeMode mode) {
        preferences.edit().putString(THEME_MODE, mode.name()).apply();
    }

    public FocusStepLimit focusStepLimit() {
        String stored = preferences.getString(FOCUS_STEP_LIMIT, FocusStepLimit.AUTO.name());
        try {
            return FocusStepLimit.valueOf(stored);
        } catch (IllegalArgumentException error) {
            logger.error(TAG, "Ignoring unsupported focus step limit: " + stored, error);
            return FocusStepLimit.AUTO;
        }
    }

    public void setFocusStepLimit(FocusStepLimit limit) {
        preferences.edit().putString(FOCUS_STEP_LIMIT, limit.name()).apply();
    }

    public int restTimerDefaultSeconds() {
        return Math.max(1, preferences.getInt(REST_TIMER_DEFAULT_SECONDS,
                DEFAULT_REST_TIMER_SECONDS));
    }

    public void setRestTimerDefaultSeconds(int seconds) {
        if (seconds < 1) throw new IllegalArgumentException("Rest timer must be positive");
        preferences.edit().putInt(REST_TIMER_DEFAULT_SECONDS, seconds).apply();
    }

    public DisplayPreferences displayPreferences() {
        return new DisplayPreferences(themeMode(), focusStepLimit(), restTimerDefaultSeconds());
    }

    public Subscription observeDisplayPreferences(Consumer<DisplayPreferences> observer) {
        if (observer == null) throw new IllegalArgumentException("Observer is required");
        SharedPreferences.OnSharedPreferenceChangeListener listener = (source, key) -> {
            if (THEME_MODE.equals(key) || FOCUS_STEP_LIMIT.equals(key)
                    || REST_TIMER_DEFAULT_SECONDS.equals(key))
                observer.accept(displayPreferences());
        };
        preferences.registerOnSharedPreferenceChangeListener(listener);
        observer.accept(displayPreferences());
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true))
                preferences.unregisterOnSharedPreferenceChangeListener(listener);
        };
    }

    public boolean calendarPermissionAsked() {
        return preferences.getBoolean(CALENDAR_ASKED, false);
    }

    public void markCalendarPermissionAsked() {
        preferences.edit().putBoolean(CALENDAR_ASKED, true).apply();
    }

    public CalendarPolicy calendarPolicy() {
        String stored = preferences.getString(CALENDAR_POLICY, CalendarPolicy.ALL_VISIBLE.name());
        try {
            return CalendarPolicy.valueOf(stored);
        } catch (IllegalArgumentException error) {
            logger.error(TAG, "Ignoring unsupported calendar policy: " + stored, error);
            return CalendarPolicy.ALL_VISIBLE;
        }
    }

    public void setCalendarPolicy(CalendarPolicy policy) {
        preferences.edit().putString(CALENDAR_POLICY, policy.name()).apply();
    }

    public Subscription observeCalendarPolicy(Consumer<CalendarPolicy> observer) {
        if (observer == null) throw new IllegalArgumentException("Observer is required");
        SharedPreferences.OnSharedPreferenceChangeListener listener = (source, key) -> {
            if (CALENDAR_POLICY.equals(key)) observer.accept(calendarPolicy());
        };
        preferences.registerOnSharedPreferenceChangeListener(listener);
        observer.accept(calendarPolicy());
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true))
                preferences.unregisterOnSharedPreferenceChangeListener(listener);
        };
    }

}
