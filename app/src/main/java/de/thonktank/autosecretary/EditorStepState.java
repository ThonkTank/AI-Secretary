package de.thonktank.autosecretary;

import android.os.Bundle;

import java.util.Objects;

import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.domain.model.RestTimerPolicy;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
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
    public final RestTimerPolicy restTimerPolicy;
    public final String note;
    public final StepActivationKind activationKind;

    public EditorStepState(String id, String text, int weekdayMask, StepAmount amount,
                           String note) {
        this(id, text, weekdayMask, 0, amount, note);
    }

    public EditorStepState(String id, String text, int weekdayMask, int intervalDays,
                           StepAmount amount, String note) {
        this(id, text, weekdayMask != 0 ? StepCadenceMode.WEEKDAYS
                        : intervalDays != 0 ? StepCadenceMode.INTERVAL : StepCadenceMode.ALWAYS,
                weekdayMask, intervalDays == 0 ? null : intervalDays, amount,
                RestTimerPolicy.forAmount(amount), note);
    }

    public EditorStepState(String id, String text, int weekdayMask, int intervalDays,
                           StepAmount amount, RestTimerPolicy restTimerPolicy, String note) {
        this(id, text, weekdayMask, intervalDays, amount, restTimerPolicy, note,
                StepActivationKind.SCHEDULED);
    }

    public EditorStepState(String id, String text, int weekdayMask, int intervalDays,
                           StepAmount amount, String note, StepActivationKind activationKind) {
        this(id, text, weekdayMask, intervalDays, amount, RestTimerPolicy.forAmount(amount), note,
                activationKind);
    }

    public EditorStepState(String id, String text, int weekdayMask, int intervalDays,
                           StepAmount amount, RestTimerPolicy restTimerPolicy, String note,
                           StepActivationKind activationKind) {
        this(id, text, weekdayMask != 0 ? StepCadenceMode.WEEKDAYS
                        : intervalDays != 0 ? StepCadenceMode.INTERVAL : StepCadenceMode.ALWAYS,
                weekdayMask, intervalDays == 0 ? null : intervalDays, amount,
                restTimerPolicy, note, activationKind);
    }

    public EditorStepState(String id, String text, StepCadenceMode cadenceMode,
                           int weekdayMask, Integer intervalDays, StepAmount amount, String note) {
        this(id, text, cadenceMode, weekdayMask, intervalDays, amount,
                RestTimerPolicy.forAmount(amount), note);
    }

    public EditorStepState(String id, String text, StepCadenceMode cadenceMode,
                           int weekdayMask, Integer intervalDays, StepAmount amount,
                           RestTimerPolicy restTimerPolicy, String note) {
        this(id, text, cadenceMode, weekdayMask, intervalDays, amount, restTimerPolicy, note,
                StepActivationKind.SCHEDULED);
    }

    public EditorStepState(String id, String text, StepCadenceMode cadenceMode,
                           int weekdayMask, Integer intervalDays, StepAmount amount,
                           RestTimerPolicy restTimerPolicy, String note,
                           StepActivationKind activationKind) {
        this.id = id;
        this.text = text == null ? "" : text;
        this.cadenceMode = cadenceMode == null ? StepCadenceMode.ALWAYS : cadenceMode;
        this.weekdayMask = this.cadenceMode == StepCadenceMode.WEEKDAYS
                ? weekdayMask & 0x7f : 0;
        this.intervalDays = this.cadenceMode == StepCadenceMode.INTERVAL ? intervalDays : null;
        this.amount = amount == null ? StepAmount.none() : amount;
        this.restTimerPolicy = this.amount instanceof StepAmount.SetsReps
                ? restTimerPolicy == null ? RestTimerPolicy.inherit() : restTimerPolicy
                : RestTimerPolicy.off();
        this.note = note == null ? "" : note;
        this.activationKind = activationKind == null
                ? StepActivationKind.SCHEDULED : activationKind;
    }

    public static EditorStepState blank(int identity) {
        return new EditorStepState(DRAFT_PREFIX + identity, "", 0, 0,
                StepAmount.none(), "");
    }

    public static EditorStepState from(TaskStepTemplate value) {
        return new EditorStepState(value.id, value.text, value.weekdayMask, value.intervalDays,
                value.amount, value.restTimerPolicy, value.note, value.activationKind);
    }

    public boolean isDraftIdentity() { return id == null || id.startsWith(DRAFT_PREFIX); }

    public TaskStepDefinition definition(int position, boolean once) {
        if (!once && cadenceMode == StepCadenceMode.INTERVAL
                && (intervalDays == null || intervalDays < 2))
            throw new IllegalStateException("A valid step interval is required before saving");
        boolean followUp = activationKind == StepActivationKind.FOLLOW_UP;
        return new TaskStepDefinition(isDraftIdentity() ? null : id, position, text,
                once || followUp || cadenceMode != StepCadenceMode.WEEKDAYS ? 0 : weekdayMask,
                once || followUp || cadenceMode != StepCadenceMode.INTERVAL ? 0 : intervalDays,
                amount, restTimerPolicy, note, activationKind);
    }

    public EditorStepState withText(String value) {
        return new EditorStepState(id, value, cadenceMode, weekdayMask, intervalDays, amount,
                restTimerPolicy, note, activationKind);
    }

    public EditorStepState withWeekdayMask(int value) {
        return new EditorStepState(id, text, StepCadenceMode.WEEKDAYS, value, null, amount,
                restTimerPolicy, note, activationKind);
    }

    public EditorStepState withIntervalDays(Integer value) {
        return new EditorStepState(id, text, StepCadenceMode.INTERVAL, 0, value, amount,
                restTimerPolicy, note, activationKind);
    }

    public EditorStepState withCadenceMode(StepCadenceMode value) {
        if (value == StepCadenceMode.WEEKDAYS)
            return new EditorStepState(id, text, value, weekdayMask == 0 ? 1 : weekdayMask,
                    null, amount, restTimerPolicy, note, activationKind);
        if (value == StepCadenceMode.INTERVAL)
            return new EditorStepState(id, text, value, 0,
                    intervalDays == null ? 2 : intervalDays, amount, restTimerPolicy, note,
                    activationKind);
        return new EditorStepState(id, text, StepCadenceMode.ALWAYS, 0, null, amount,
                restTimerPolicy, note, activationKind);
    }

    public EditorStepState withAmount(StepAmount value) {
        RestTimerPolicy rest = value instanceof StepAmount.SetsReps
                ? amount instanceof StepAmount.SetsReps ? restTimerPolicy
                : RestTimerPolicy.inherit() : RestTimerPolicy.off();
        return new EditorStepState(id, text, cadenceMode, weekdayMask, intervalDays, value,
                rest, note, activationKind);
    }

    public EditorStepState withRestTimerPolicy(RestTimerPolicy value) {
        return new EditorStepState(id, text, cadenceMode, weekdayMask, intervalDays, amount,
                value, note, activationKind);
    }

    public EditorStepState withNote(String value) {
        return new EditorStepState(id, text, cadenceMode, weekdayMask, intervalDays, amount,
                restTimerPolicy, value, activationKind);
    }

    Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString("id", id); bundle.putString("text", text);
        bundle.putString("cadence", cadenceMode.name());
        bundle.putInt("weekdays", weekdayMask); putInteger(bundle, "interval", intervalDays);
        bundle.putString("amount", amount.kind().name());
        bundle.putString("rest_timer_mode", restTimerPolicy.mode.name());
        putInteger(bundle, "rest_timer_seconds", restTimerPolicy.customSeconds);
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
        bundle.putString("activation", activationKind.name());
        return bundle;
    }

    static EditorStepState fromBundle(Bundle bundle) {
        int weekdays = bundle.getInt("weekdays");
        Integer interval = bundle.containsKey("cadence") ? integer(bundle, "interval")
                : bundle.getInt("interval") == 0 ? null : bundle.getInt("interval");
        StepCadenceMode cadence = cadence(bundle.getString("cadence"), weekdays, interval);
        StepAmount amount = StepAmount.fromStorage(enumValue(bundle.getString("amount")),
                integer(bundle, "sets"), integer(bundle, "reps"), integer(bundle, "duration"));
        return new EditorStepState(bundle.getString("id"), bundle.getString("text", ""),
                cadence, weekdays, interval, amount,
                RestTimerPolicy.fromStorage(bundle.getString("rest_timer_mode"),
                        integer(bundle, "rest_timer_seconds")),
                bundle.getString("note", ""), BundleValues.enumValue(StepActivationKind.class,
                bundle.getString("activation"), StepActivationKind.SCHEDULED));
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
                && restTimerPolicy.equals(value.restTimerPolicy)
                && note.equals(value.note) && activationKind == value.activationKind;
    }

    @Override public int hashCode() {
        return Objects.hash(id, text, cadenceMode, weekdayMask, intervalDays, amount,
                restTimerPolicy, note, activationKind);
    }
}
