package de.thonktank.autosecretary;

import android.os.Bundle;

import java.util.Objects;

import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

public final class EditorStepState {
    private static final String DRAFT_PREFIX = "draft:";
    public final String id;
    public final String text;
    public final StepCadenceMode cadenceMode;
    public final int weekdayMask;
    public final Integer intervalDays;
    public final StepAmount amount;
    public final String note;

    public EditorStepState(String id, String text, int weekdayMask, StepAmount amount,
                           String note) {
        this(id, text, weekdayMask, 0, amount, note);
    }

    public EditorStepState(String id, String text, int weekdayMask, int intervalDays,
                           StepAmount amount, String note) {
        this(id, text, weekdayMask != 0 ? StepCadenceMode.WEEKDAYS
                        : intervalDays != 0 ? StepCadenceMode.INTERVAL : StepCadenceMode.ALWAYS,
                weekdayMask, intervalDays == 0 ? null : intervalDays, amount, note);
    }

    public EditorStepState(String id, String text, StepCadenceMode cadenceMode,
                           int weekdayMask, Integer intervalDays, StepAmount amount, String note) {
        this.id = id;
        this.text = text == null ? "" : text;
        this.cadenceMode = cadenceMode == null ? StepCadenceMode.ALWAYS : cadenceMode;
        this.weekdayMask = this.cadenceMode == StepCadenceMode.WEEKDAYS
                ? weekdayMask & 0x7f : 0;
        this.intervalDays = this.cadenceMode == StepCadenceMode.INTERVAL ? intervalDays : null;
        this.amount = amount == null ? StepAmount.none() : amount;
        this.note = note == null ? "" : note;
    }

    public static EditorStepState blank(int identity) {
        return new EditorStepState(DRAFT_PREFIX + identity, "", 0, 0,
                StepAmount.none(), "");
    }

    public static EditorStepState from(TaskStepTemplate value) {
        return new EditorStepState(value.id, value.text, value.weekdayMask, value.intervalDays,
                value.amount, value.note);
    }

    public boolean isDraftIdentity() { return id == null || id.startsWith(DRAFT_PREFIX); }

    public TaskStepDefinition definition(int position, boolean once) {
        if (!once && cadenceMode == StepCadenceMode.INTERVAL
                && (intervalDays == null || intervalDays < 2))
            throw new IllegalStateException("A valid step interval is required before saving");
        return new TaskStepDefinition(isDraftIdentity() ? null : id, position, text,
                once || cadenceMode != StepCadenceMode.WEEKDAYS ? 0 : weekdayMask,
                once || cadenceMode != StepCadenceMode.INTERVAL ? 0 : intervalDays,
                amount, note);
    }

    public EditorStepState withText(String value) {
        return new EditorStepState(id, value, cadenceMode, weekdayMask, intervalDays, amount, note);
    }

    public EditorStepState withWeekdayMask(int value) {
        return new EditorStepState(id, text, StepCadenceMode.WEEKDAYS, value, null, amount, note);
    }

    public EditorStepState withIntervalDays(Integer value) {
        return new EditorStepState(id, text, StepCadenceMode.INTERVAL, 0, value, amount, note);
    }

    public EditorStepState withCadenceMode(StepCadenceMode value) {
        if (value == StepCadenceMode.WEEKDAYS)
            return new EditorStepState(id, text, value, weekdayMask == 0 ? 1 : weekdayMask,
                    null, amount, note);
        if (value == StepCadenceMode.INTERVAL)
            return new EditorStepState(id, text, value, 0,
                    intervalDays == null ? 2 : intervalDays, amount, note);
        return new EditorStepState(id, text, StepCadenceMode.ALWAYS, 0, null, amount, note);
    }

    public EditorStepState withAmount(StepAmount value) {
        return new EditorStepState(id, text, cadenceMode, weekdayMask, intervalDays, value, note);
    }

    public EditorStepState withNote(String value) {
        return new EditorStepState(id, text, cadenceMode, weekdayMask, intervalDays, amount, value);
    }

    Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString("id", id); bundle.putString("text", text);
        bundle.putString("cadence", cadenceMode.name());
        bundle.putInt("weekdays", weekdayMask); putInteger(bundle, "interval", intervalDays);
        bundle.putString("amount", amount.kind().name());
        if (amount instanceof StepAmount.SetsReps) {
            StepAmount.SetsReps value = (StepAmount.SetsReps) amount;
            putInteger(bundle, "sets", value.sets);
            putInteger(bundle, "reps", value.repetitions);
        } else if (amount instanceof StepAmount.Repetitions) {
            putInteger(bundle, "reps", ((StepAmount.Repetitions) amount).repetitions);
        } else if (amount instanceof StepAmount.Duration) {
            putInteger(bundle, "duration", ((StepAmount.Duration) amount).seconds);
        }
        bundle.putString("note", note);
        return bundle;
    }

    static EditorStepState fromBundle(Bundle bundle) {
        int weekdays = bundle.getInt("weekdays");
        Integer interval = bundle.containsKey("cadence") ? integer(bundle, "interval")
                : bundle.getInt("interval") == 0 ? null : bundle.getInt("interval");
        StepCadenceMode cadence = cadence(bundle.getString("cadence"), weekdays, interval);
        return new EditorStepState(bundle.getString("id"), bundle.getString("text", ""),
                cadence, weekdays, interval, StepAmount.fromStorage(
                        enumValue(bundle.getString("amount")), integer(bundle, "sets"),
                        integer(bundle, "reps"), integer(bundle, "duration")),
                bundle.getString("note", ""));
    }

    private static StepCadenceMode cadence(String value, int weekdays, Integer interval) {
        try { return value == null ? weekdays != 0 ? StepCadenceMode.WEEKDAYS
                : interval != null ? StepCadenceMode.INTERVAL : StepCadenceMode.ALWAYS
                : StepCadenceMode.valueOf(value); }
        catch (IllegalArgumentException error) { return StepCadenceMode.ALWAYS; }
    }

    private static StepAmountKind enumValue(String value) {
        try { return StepAmountKind.valueOf(value); }
        catch (RuntimeException error) { return StepAmountKind.NONE; }
    }
    private static void putInteger(Bundle bundle, String key, Integer value) {
        if (value != null) { bundle.putBoolean(key + "_set", true); bundle.putInt(key, value); }
    }
    private static Integer integer(Bundle bundle, String key) {
        return bundle.getBoolean(key + "_set") ? bundle.getInt(key) : null;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof EditorStepState)) return false;
        EditorStepState value = (EditorStepState) other;
        return Objects.equals(id, value.id) && text.equals(value.text)
                && cadenceMode == value.cadenceMode && weekdayMask == value.weekdayMask
                && Objects.equals(intervalDays, value.intervalDays)
                && amount.equals(value.amount)
                && note.equals(value.note);
    }

    @Override public int hashCode() {
        return Objects.hash(id, text, cadenceMode, weekdayMask, intervalDays, amount, note);
    }
}
