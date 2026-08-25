package de.thonktank.autosecretary.presentation.options;

import androidx.annotation.Nullable;

import de.thonktank.autosecretary.CalendarPermissionStatus;
import de.thonktank.autosecretary.CalendarUiState;
import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.update.presentation.UpdateUiState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Complete atomic render state for the options and updater screen. */
public final class OptionsScreenState {
    public final DayPalette palette;
    public final UiThemeMode themeMode;
    public final FocusStepLimit focusStepLimit;
    public final int restTimerDefaultSeconds;
    public final CalendarPermissionStatus calendarPermission;
    public final CalendarUiState calendar;
    public final UpdateUiState update;
    public final List<OptionsRequest> requests;

    public OptionsScreenState(DayPalette palette, UiThemeMode themeMode,
                              FocusStepLimit focusStepLimit, int restTimerDefaultSeconds,
                              CalendarPermissionStatus calendarPermission,
                              CalendarUiState calendar, UpdateUiState update,
                              List<OptionsRequest> requests) {
        if (palette == null || themeMode == null || focusStepLimit == null
                || restTimerDefaultSeconds < 1
                || calendarPermission == null || calendar == null || update == null
                || requests == null)
            throw new IllegalArgumentException("Complete options state is required");
        this.palette = palette;
        this.themeMode = themeMode;
        this.focusStepLimit = focusStepLimit;
        this.restTimerDefaultSeconds = restTimerDefaultSeconds;
        this.calendarPermission = calendarPermission;
        this.calendar = calendar;
        this.update = update;
        this.requests = Collections.unmodifiableList(new ArrayList<>(requests));
    }

    public OptionsScreenState withAppearance(DayPalette palette, UiThemeMode theme,
                                             FocusStepLimit limit, int restTimerDefaultSeconds) {
        return new OptionsScreenState(palette, theme, limit, restTimerDefaultSeconds,
                calendarPermission, calendar, update, requests);
    }
    public OptionsScreenState withPermission(CalendarPermissionStatus permission) {
        return new OptionsScreenState(palette, themeMode, focusStepLimit,
                restTimerDefaultSeconds, permission, calendar, update, requests);
    }
    public OptionsScreenState withCalendar(CalendarUiState value) {
        return new OptionsScreenState(palette, themeMode, focusStepLimit,
                restTimerDefaultSeconds, calendarPermission, value, update, requests);
    }
    public OptionsScreenState withUpdate(UpdateUiState value) {
        return new OptionsScreenState(palette, themeMode, focusStepLimit,
                restTimerDefaultSeconds, calendarPermission, calendar, value, requests);
    }
    public OptionsScreenState enqueue(OptionsRequest request) {
        for (OptionsRequest pending : requests) if (pending.sameWorkAs(request)) return this;
        ArrayList<OptionsRequest> next = new ArrayList<>(requests);
        next.add(request);
        return withRequests(next);
    }
    public OptionsScreenState acknowledge(String id) {
        ArrayList<OptionsRequest> next = new ArrayList<>(requests.size());
        for (OptionsRequest request : requests) if (!request.id.equals(id)) next.add(request);
        return next.size() == requests.size() ? this : withRequests(next);
    }
    @Nullable public OptionsRequest firstRequest() {
        return requests.isEmpty() ? null : requests.get(0);
    }
    @Nullable public OptionsRequest request(String id) {
        for (OptionsRequest request : requests) if (request.id.equals(id)) return request;
        return null;
    }
    private OptionsScreenState withRequests(List<OptionsRequest> value) {
        return new OptionsScreenState(palette, themeMode, focusStepLimit,
                restTimerDefaultSeconds, calendarPermission, calendar, update, value);
    }
}
