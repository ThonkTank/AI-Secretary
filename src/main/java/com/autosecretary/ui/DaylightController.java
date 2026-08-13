package com.autosecretary.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.autosecretary.application.LocationPort;

import java.time.LocalTime;

/** Owns daylight theme, coarse-location observation, greeting and lifecycle timers. */
public final class DaylightController {
    private static final String PREFERENCES = "waldmorgen_ui";
    private static final String THEME_MODE = "theme_mode";
    private static final String LOCATION_ASKED = "location_asked";

    private final AppCompatActivity activity;
    private final View root;
    private final DaylightBackdropView backdrop;
    private final TextView themeAction;
    private final TextView greeting;
    private final Runnable requestLocationPermission;
    private final Runnable paletteRefresh;
    private final LocationPort location;
    private boolean started;
    private final Runnable minuteRefresh = new Runnable() {
        @Override public void run() {
            updateGreeting();
            DaylightBackdropView.Mode mode = storedMode();
            if (mode == DaylightBackdropView.Mode.AUTO) applyCardMode(mode);
            paletteRefresh.run();
            root.postDelayed(this, 60_000L);
        }
    };

    public DaylightController(
            AppCompatActivity activity,
            View root,
            DaylightBackdropView backdrop,
            TextView themeAction,
            TextView greeting,
            LocationPort location,
            Runnable requestLocationPermission,
            Runnable paletteRefresh) {
        this.activity = activity;
        this.root = root;
        this.backdrop = backdrop;
        this.themeAction = themeAction;
        this.greeting = greeting;
        this.location = location;
        this.requestLocationPermission = requestLocationPermission;
        this.paletteRefresh = paletteRefresh;
    }

    public void configure() {
        DaylightBackdropView.Mode mode = storedMode();
        backdrop.setMode(mode);
        themeAction.setText(switch (mode) {
            case AUTO -> "◐";
            case LIGHT -> "☀";
            case DARK -> "☾";
        });
        if (hasLocationPermission()) {
            updateLocation();
        } else if (!preferences().getBoolean(LOCATION_ASKED, false)) {
            preferences().edit().putBoolean(LOCATION_ASKED, true).apply();
            requestLocationPermission.run();
        }
        applyCardMode(mode);
        updateGreeting();
        paletteRefresh.run();
    }

    public void onStart() {
        started = true;
        root.removeCallbacks(minuteRefresh);
        root.postDelayed(minuteRefresh, 60_000L);
        startLocationUpdates();
    }

    public void onStop() {
        started = false;
        root.removeCallbacks(minuteRefresh);
        location.stop();
    }

    public void onLocationPermissionResult(boolean granted) {
        if (!granted) return;
        updateLocation();
        if (started) startLocationUpdates();
    }

    public void cycleMode() {
        DaylightBackdropView.Mode next = switch (storedMode()) {
            case AUTO -> DaylightBackdropView.Mode.LIGHT;
            case LIGHT -> DaylightBackdropView.Mode.DARK;
            case DARK -> DaylightBackdropView.Mode.AUTO;
        };
        preferences().edit().putString(THEME_MODE, next.name()).apply();
        backdrop.setMode(next);
        applyCardMode(next);
        activity.recreate();
    }

    private void updateLocation() {
        if (!hasLocationPermission()) return;
        applyLocation(location.lastKnown());
    }

    private void startLocationUpdates() {
        if (!hasLocationPermission()) return;
        location.start(this::applyLocation);
    }

    private void applyLocation(LocationPort.Position location) {
        if (location == null) return;
        backdrop.setCoordinates(location.latitude(), location.longitude());
        DaylightBackdropView.Mode mode = storedMode();
        if (mode == DaylightBackdropView.Mode.AUTO) applyCardMode(mode);
        paletteRefresh.run();
    }

    private void updateGreeting() {
        int minute = LocalTime.now().getHour() * 60 + LocalTime.now().getMinute();
        greeting.setText(minute < 5 * 60 ? "Noch früh"
                : minute < 9 * 60 ? "Guten Morgen"
                : minute < 12 * 60 ? "Vormittag"
                : minute < 14 * 60 ? "Mittag"
                : minute < 18 * 60 ? "Nachmittag"
                : minute < 21 * 60 ? "Guten Abend"
                : minute < 23 * 60 ? "Es wird spät" : "Gute Nacht");
    }

    private void applyCardMode(DaylightBackdropView.Mode mode) {
        int target = switch (mode) {
            case LIGHT -> AppCompatDelegate.MODE_NIGHT_NO;
            case DARK -> AppCompatDelegate.MODE_NIGHT_YES;
            case AUTO -> backdrop.wantsLightCards()
                    ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
        };
        if (activity.getDelegate().getLocalNightMode() != target) {
            activity.getDelegate().setLocalNightMode(target);
        }
    }

    private DaylightBackdropView.Mode storedMode() {
        String stored = preferences().getString(THEME_MODE, DaylightBackdropView.Mode.AUTO.name());
        try { return DaylightBackdropView.Mode.valueOf(stored); }
        catch (IllegalArgumentException ignored) { return DaylightBackdropView.Mode.AUTO; }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private android.content.SharedPreferences preferences() {
        return activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
