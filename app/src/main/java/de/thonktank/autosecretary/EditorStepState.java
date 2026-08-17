package de.thonktank.autosecretary;

import android.os.Bundle;

import java.util.Objects;

import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

public final class EditorStepState {
    private static final String DRAFT_PREFIX = "draft:";
    public final String id;
    public final String text;
    public final int weekdayMask;
    public final StepAmountKind amountKind;
    public final Integer plannedSets;
    public final Integer plannedReps;
    public final Integer plannedDurationSeconds;
    public final String note;

    public EditorStepState(String id, String text, int weekdayMask, StepAmountKind amountKind,
                           Integer plannedSets, Integer plannedReps,
                           Integer plannedDurationSeconds, String note) {
        this.id = id;
        this.text = text == null ? "" : text;
        this.weekdayMask = weekdayMask & 0x7f;
        this.amountKind = amountKind == null ? StepAmountKind.NONE : amountKind;
        this.plannedSets = plannedSets;
        this.plannedReps = plannedReps;
        this.plannedDurationSeconds = plannedDurationSeconds;
        this.note = note == null ? "" : note;
    }

    public static EditorStepState blank(int identity) {
        return new EditorStepState(DRAFT_PREFIX + identity, "", 0, StepAmountKind.NONE,
                null, null, null, "");
    }

    public static EditorStepState from(TaskStepTemplate value) {
        return new EditorStepState(value.id, value.text, value.weekdayMask, value.amountKind,
                value.plannedSets, value.plannedReps, value.plannedDurationSeconds, value.note);
    }

    public boolean isDraftIdentity() { return id == null || id.startsWith(DRAFT_PREFIX); }

    public TaskStepDefinition definition(int position, boolean once) {
        return new TaskStepDefinition(isDraftIdentity() ? null : id, position, text,
                once ? 0 : weekdayMask, amountKind, plannedSets, plannedReps,
                plannedDurationSeconds, note);
    }

    public EditorStepState withText(String value) {
        return new EditorStepState(id, value, weekdayMask, amountKind, plannedSets,
                plannedReps, plannedDurationSeconds, note);
    }

    public EditorStepState withWeekdayMask(int value) {
        return new EditorStepState(id, text, value, amountKind, plannedSets, plannedReps,
                plannedDurationSeconds, note);
    }

    public EditorStepState withAmount(StepAmountKind kind, Integer sets, Integer reps,
                                      Integer duration) {
        return new EditorStepState(id, text, weekdayMask, kind, sets, reps, duration, note);
    }

    public EditorStepState withNote(String value) {
        return new EditorStepState(id, text, weekdayMask, amountKind, plannedSets, plannedReps,
                plannedDurationSeconds, value);
    }

    Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString("id", id); bundle.putString("text", text);
        bundle.putInt("weekdays", weekdayMask); bundle.putString("amount", amountKind.name());
        putInteger(bundle, "sets", plannedSets); putInteger(bundle, "reps", plannedReps);
        putInteger(bundle, "duration", plannedDurationSeconds); bundle.putString("note", note);
        return bundle;
    }

    static EditorStepState fromBundle(Bundle bundle) {
        return new EditorStepState(bundle.getString("id"), bundle.getString("text", ""),
                bundle.getInt("weekdays"), enumValue(bundle.getString("amount")),
                integer(bundle, "sets"), integer(bundle, "reps"),
                integer(bundle, "duration"), bundle.getString("note", ""));
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
                && weekdayMask == value.weekdayMask && amountKind == value.amountKind
                && Objects.equals(plannedSets, value.plannedSets)
                && Objects.equals(plannedReps, value.plannedReps)
                && Objects.equals(plannedDurationSeconds, value.plannedDurationSeconds)
                && note.equals(value.note);
    }

    @Override public int hashCode() {
        return Objects.hash(id, text, weekdayMask, amountKind, plannedSets, plannedReps,
                plannedDurationSeconds, note);
    }
}
