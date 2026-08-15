package de.thonktank.autosecretary;

import android.os.Bundle;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskDetails;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EditorUiState {
    private static final String OPEN = "open";
    private static final String LOADING = "loading";
    private static final String TASK_ID = "task_id";
    private static final String TITLE = "title";
    private static final String SLOT = "slot";
    private static final String RECURRENCE = "recurrence";
    private static final String INTERVAL = "interval";
    private static final String WEEKDAYS = "weekdays";
    private static final String STEPS = "steps";
    private static final String ONGOING = "ongoing";
    private static final String CONDITION = "condition";

    public final boolean open;
    public final boolean loading;
    public final String taskId;
    public final String title;
    public final TaskSlot slot;
    public final Recurrence recurrence;
    public final int intervalDays;
    public final int weekdayMask;
    public final List<String> steps;
    public final boolean ongoing;
    public final String condition;

    private EditorUiState(boolean open, boolean loading, String taskId, String title,
                          TaskSlot slot, Recurrence recurrence, int intervalDays,
                          int weekdayMask, List<String> steps, boolean ongoing,
                          String condition) {
        this.open = open;
        this.loading = loading;
        this.taskId = taskId;
        this.title = title;
        this.slot = slot;
        this.recurrence = recurrence;
        this.intervalDays = intervalDays;
        this.weekdayMask = weekdayMask;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.ongoing = ongoing;
        this.condition = condition;
    }

    public static EditorUiState closed() {
        return new EditorUiState(false, false, null, "", TaskSlot.MORNING,
                Recurrence.ONCE, 2, 0, Collections.emptyList(), false, "");
    }

    public static EditorUiState create() {
        return new EditorUiState(true, false, null, "", TaskSlot.MORNING,
                Recurrence.ONCE, 2, 0, Collections.emptyList(), false, "");
    }

    public static EditorUiState loading(String taskId) {
        return new EditorUiState(true, true, taskId, "", TaskSlot.MORNING,
                Recurrence.ONCE, 2, 0, Collections.emptyList(), false, "");
    }

    public static EditorUiState edit(TaskDetails details) {
        return new EditorUiState(true, false, details.id.value, details.title, details.slot,
                details.recurrence, details.intervalDays, details.weekdayMask, details.steps,
                details.ongoing, details.condition);
    }

    public EditorUiState withDraft(String title, TaskSlot slot, Recurrence recurrence,
                                   int intervalDays, int weekdayMask, List<String> steps,
                                   boolean ongoing, String condition) {
        return new EditorUiState(true, false, taskId, title, slot, recurrence, intervalDays,
                weekdayMask, steps, ongoing, condition);
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(OPEN, open);
        bundle.putBoolean(LOADING, loading);
        bundle.putString(TASK_ID, taskId);
        bundle.putString(TITLE, title);
        bundle.putString(SLOT, slot.name());
        bundle.putString(RECURRENCE, recurrence.name());
        bundle.putInt(INTERVAL, intervalDays);
        bundle.putInt(WEEKDAYS, weekdayMask);
        bundle.putStringArrayList(STEPS, new ArrayList<>(steps));
        bundle.putBoolean(ONGOING, ongoing);
        bundle.putString(CONDITION, condition);
        return bundle;
    }

    public static EditorUiState fromBundle(Bundle bundle) {
        if (bundle == null || !bundle.getBoolean(OPEN, false)) return closed();
        String taskId = bundle.getString(TASK_ID);
        if (bundle.getBoolean(LOADING, false) && taskId != null) return loading(taskId);
        ArrayList<String> steps = bundle.getStringArrayList(STEPS);
        return new EditorUiState(true, false, taskId, bundle.getString(TITLE, ""),
                enumValue(TaskSlot.class, bundle.getString(SLOT), TaskSlot.MORNING),
                enumValue(Recurrence.class, bundle.getString(RECURRENCE), Recurrence.ONCE),
                Math.max(1, bundle.getInt(INTERVAL, 2)), bundle.getInt(WEEKDAYS, 0),
                steps == null ? Collections.emptyList() : steps,
                bundle.getBoolean(ONGOING, false), bundle.getString(CONDITION, ""));
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException error) {
            return fallback;
        }
    }
}
