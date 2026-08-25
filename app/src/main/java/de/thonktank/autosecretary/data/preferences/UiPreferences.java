package de.thonktank.autosecretary.data.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.calendar.CalendarPolicy;
import de.thonktank.autosecretary.domain.model.ComboDecayTrigger;
import de.thonktank.autosecretary.domain.model.ComboPolicy;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;

public final class UiPreferences implements ComboPolicySource {
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
    private static final String COMBO_GAIN_POINTS = "combo_gain_points";
    private static final String COMBO_DECAY_POINTS = "combo_decay_points";
    private static final String COMBO_DECAY_TRIGGER = "combo_decay_trigger";
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
        return new DisplayPreferences(themeMode(), focusStepLimit(), restTimerDefaultSeconds(),
                current());
    }

    @Override public ComboPolicy current() {
        int gain = Math.max(0, preferences.getInt(COMBO_GAIN_POINTS,
                ComboPolicy.DEFAULT_GAIN_POINTS));
        int decay = Math.max(0, preferences.getInt(COMBO_DECAY_POINTS,
                ComboPolicy.DEFAULT_DECAY_POINTS));
        String stored = preferences.getString(COMBO_DECAY_TRIGGER,
                ComboDecayTrigger.DAILY_OVERDUE.name());
        ComboDecayTrigger trigger;
        try {
            trigger = ComboDecayTrigger.valueOf(stored);
        } catch (IllegalArgumentException error) {
            logger.error(TAG, "Ignoring unsupported combo decay trigger: " + stored, error);
            trigger = ComboDecayTrigger.DAILY_OVERDUE;
        }
        return new ComboPolicy(gain, decay, trigger);
    }

    public void setComboPolicy(ComboPolicy policy) {
        if (policy == null) throw new IllegalArgumentException("Combo policy is required");
        preferences.edit()
                .putInt(COMBO_GAIN_POINTS, policy.gainPoints)
                .putInt(COMBO_DECAY_POINTS, policy.decayPoints)
                .putString(COMBO_DECAY_TRIGGER, policy.trigger.name())
                .apply();
    }

    public Subscription observeDisplayPreferences(Consumer<DisplayPreferences> observer) {
        if (observer == null) throw new IllegalArgumentException("Observer is required");
        SharedPreferences.OnSharedPreferenceChangeListener listener = (source, key) -> {
            if (THEME_MODE.equals(key) || FOCUS_STEP_LIMIT.equals(key)
                    || REST_TIMER_DEFAULT_SECONDS.equals(key)
                    || COMBO_GAIN_POINTS.equals(key) || COMBO_DECAY_POINTS.equals(key)
                    || COMBO_DECAY_TRIGGER.equals(key))
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
