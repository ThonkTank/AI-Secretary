package de.thonktank.autosecretary;

import java.util.Objects;

import de.thonktank.autosecretary.domain.model.RestTimerPolicy;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.StepPrescription;

public final class EditorStepState {
    private static final String DRAFT_PREFIX = "draft:";
    public final String id;
    public final String text;
    public final StepCadenceMode cadenceMode;
    public final int weekdayMask;
    public final Integer intervalDays;
    public final StepPrescription prescription;
    public final String note;
    public final StepActivationKind activationKind;

    public EditorStepState(String id, String text, StepCadenceMode cadenceMode,
                            int weekdayMask, Integer intervalDays,
                            StepPrescription prescription, String note,
                            StepActivationKind activationKind) {
        this.id = id;
        this.text = text == null ? "" : text;
        this.cadenceMode = cadenceMode == null ? StepCadenceMode.ALWAYS : cadenceMode;
        this.weekdayMask = this.cadenceMode == StepCadenceMode.WEEKDAYS
                ? weekdayMask & 0x7f : 0;
        this.intervalDays = this.cadenceMode == StepCadenceMode.INTERVAL ? intervalDays : null;
        this.prescription = Objects.requireNonNull(prescription, "prescription");
        this.note = note == null ? "" : note;
        this.activationKind = activationKind == null
                ? StepActivationKind.SCHEDULED : activationKind;
    }

    public static EditorStepState blank(int identity) {
        return new EditorStepState(DRAFT_PREFIX + identity, "", StepCadenceMode.ALWAYS, 0, null,
                StepPrescription.forAmount(StepAmount.none()), "",
                StepActivationKind.SCHEDULED);
    }

    public static EditorStepState from(TaskStepTemplate value) {
        StepCadenceMode cadence = value.weekdayMask != 0 ? StepCadenceMode.WEEKDAYS
                : value.intervalDays != 0 ? StepCadenceMode.INTERVAL : StepCadenceMode.ALWAYS;
        return new EditorStepState(value.id, value.text, cadence, value.weekdayMask,
                value.intervalDays == 0 ? null : value.intervalDays, value.prescription,
                value.note, value.activationKind);
    }

    static EditorStepState fromStored(String id, String text, StepCadenceMode cadenceMode,
                                      int weekdayMask, Integer intervalDays,
                                      StepPrescription prescription,
                                      String note, StepActivationKind activationKind) {
        return new EditorStepState(id, text, cadenceMode, weekdayMask, intervalDays,
                prescription,
                note, activationKind);
    }

    public boolean isDraftIdentity() { return id == null || id.startsWith(DRAFT_PREFIX); }

    public TaskStepDefinition definition(int position, boolean once) {
        return definition(position, once, activationKind);
    }

    public TaskStepDefinition definition(int position, boolean once,
                                         StepActivationKind activation) {
        StepActivationKind resolved = activation == null
                ? StepActivationKind.SCHEDULED : activation;
        if (!once && resolved != StepActivationKind.FOLLOW_UP
                && cadenceMode == StepCadenceMode.INTERVAL
                && (intervalDays == null || intervalDays < 2))
            throw new IllegalStateException("A valid step interval is required before saving");
        boolean followUp = resolved == StepActivationKind.FOLLOW_UP;
        return new TaskStepDefinition(isDraftIdentity() ? null : id, position, text,
                once || followUp || cadenceMode != StepCadenceMode.WEEKDAYS ? 0 : weekdayMask,
                once || followUp || cadenceMode != StepCadenceMode.INTERVAL ? 0 : intervalDays,
                prescription, note, resolved);
    }

    public EditorStepState withText(String value) {
        return copy(value, cadenceMode, weekdayMask, intervalDays, prescription,
                note);
    }

    public EditorStepState withWeekdayMask(int value) {
        return copy(text, StepCadenceMode.WEEKDAYS, value, null, prescription,
                note);
    }

    public EditorStepState withIntervalDays(Integer value) {
        return copy(text, StepCadenceMode.INTERVAL, 0, value, prescription,
                note);
    }

    public EditorStepState withCadenceMode(StepCadenceMode value) {
        if (value == StepCadenceMode.WEEKDAYS)
            return copy(text, value, weekdayMask == 0 ? 1 : weekdayMask, null, prescription,
                    note);
        if (value == StepCadenceMode.INTERVAL)
            return copy(text, value, 0, intervalDays == null ? 2 : intervalDays, prescription,
                    note);
        return copy(text, StepCadenceMode.ALWAYS, 0, null, prescription,
                note);
    }

    public EditorStepState withAmount(StepAmount value) {
        RestTimerPolicy rest = value instanceof StepAmount.SetsReps
                ? prescription.amount instanceof StepAmount.SetsReps ? prescription.rest
                : RestTimerPolicy.inherit() : RestTimerPolicy.off();
        return copy(text, cadenceMode, weekdayMask, intervalDays,
                new StepPrescription(value, rest),
                note);
    }

    public EditorStepState withRestTimerPolicy(RestTimerPolicy value) {
        return copy(text, cadenceMode, weekdayMask, intervalDays,
                new StepPrescription(prescription.amount, value),
                note);
    }



    public EditorStepState withNote(String value) {
        return copy(text, cadenceMode, weekdayMask, intervalDays, prescription,
                value);
    }



    private EditorStepState copy(String newText, StepCadenceMode cadence, int weekdays,
                                 Integer interval, StepPrescription newPrescription,
                                 String newNote) {
        return new EditorStepState(id, newText, cadence, weekdays, interval, newPrescription,
                newNote, activationKind);
    }

    android.os.Bundle toBundle() { return EditorStepSavedStateCodec.encode(this); }

    static EditorStepState fromBundle(android.os.Bundle bundle) {
        return EditorStepSavedStateCodec.decode(bundle);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof EditorStepState)) return false;
        EditorStepState value = (EditorStepState) other;
        return Objects.equals(id, value.id) && text.equals(value.text)
                && cadenceMode == value.cadenceMode && weekdayMask == value.weekdayMask
                && Objects.equals(intervalDays, value.intervalDays)
                && prescription.equals(value.prescription)
                && note.equals(value.note) && activationKind == value.activationKind;
    }

    @Override public int hashCode() {
        return Objects.hash(id, text, cadenceMode, weekdayMask, intervalDays, prescription,
                note, activationKind);
    }
}
