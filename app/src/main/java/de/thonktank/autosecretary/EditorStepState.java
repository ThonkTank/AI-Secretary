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
    public final int weekdayMask;
    public final int intervalDays;
    public final StepAmount amount;
    public final String note;

    public EditorStepState(String id, String text, int weekdayMask, StepAmount amount,
                           String note) {
        this(id, text, weekdayMask, 0, amount, note);
    }

    public EditorStepState(String id, String text, int weekdayMask, int intervalDays,
                           StepAmount amount, String note) {
        this.id = id;
        this.text = text == null ? "" : text;
        this.weekdayMask = weekdayMask & 0x7f;
        this.intervalDays = this.weekdayMask == 0 ? Math.max(0, intervalDays) : 0;
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
        return new TaskStepDefinition(isDraftIdentity() ? null : id, position, text,
                once ? 0 : weekdayMask, once ? 0 : intervalDays, amount, note);
    }

    public EditorStepState withText(String value) {
        return new EditorStepState(id, value, weekdayMask, intervalDays, amount, note);
    }

    public EditorStepState withWeekdayMask(int value) {
        return new EditorStepState(id, text, value, 0, amount, note);
    }

    public EditorStepState withIntervalDays(int value) {
        return new EditorStepState(id, text, 0, value, amount, note);
    }

    public EditorStepState withAmount(StepAmount value) {
        return new EditorStepState(id, text, weekdayMask, intervalDays, value, note);
    }

    public EditorStepState withNote(String value) {
        return new EditorStepState(id, text, weekdayMask, intervalDays, amount, value);
    }

    Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString("id", id); bundle.putString("text", text);
        bundle.putInt("weekdays", weekdayMask); bundle.putInt("interval", intervalDays);
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
        return new EditorStepState(bundle.getString("id"), bundle.getString("text", ""),
                bundle.getInt("weekdays"), bundle.getInt("interval"), StepAmount.fromStorage(
                        enumValue(bundle.getString("amount")), integer(bundle, "sets"),
                        integer(bundle, "reps"), integer(bundle, "duration")),
                bundle.getString("note", ""));
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
                && weekdayMask == value.weekdayMask && intervalDays == value.intervalDays
                && amount.equals(value.amount)
                && note.equals(value.note);
    }

    @Override public int hashCode() {
        return Objects.hash(id, text, weekdayMask, intervalDays, amount, note);
    }
}
