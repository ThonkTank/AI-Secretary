package de.thonktank.autosecretary;

import java.util.Objects;

import de.thonktank.autosecretary.domain.model.RestTimerPolicy;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAssistantConfig;
import de.thonktank.autosecretary.domain.model.TrainingAssistantPolicy;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.StepPrescription;

public final class EditorStepState {
    private static final String DRAFT_PREFIX = "draft:";
    public final String id;
    public final String text;
    public final StepCadenceMode cadenceMode;
    public final int weekdayMask;
    public final Integer intervalDays;
    public final StepAmount amount;
    public final RestTimerPolicy restTimerPolicy;
    public final TrainingAssistantConfig trainingAssistant;
    public final StepPrescription prescription;
    public final TrainingAssistantPolicy assistantPolicy;
    public final TrainingAssistantState assistantState;
    public final String note;
    public final StepActivationKind activationKind;

    public EditorStepState(String id, String text, StepCadenceMode cadenceMode,
                           int weekdayMask, Integer intervalDays,
                           StepPrescription prescription,
                           TrainingAssistantPolicy assistantPolicy, String note,
                           StepActivationKind activationKind) {
        this(id, text, cadenceMode, weekdayMask, intervalDays, prescription,
                assistantPolicy, assistantPolicy == null ? TrainingAssistantState.disabled()
                        : TrainingAssistantState.calibrating(), note, activationKind);
    }

    private EditorStepState(String id, String text, StepCadenceMode cadenceMode,
                            int weekdayMask, Integer intervalDays,
                            StepPrescription prescription,
                            TrainingAssistantPolicy assistantPolicy,
                            TrainingAssistantState assistantState, String note,
                            StepActivationKind activationKind) {
        this.id = id;
        this.text = text == null ? "" : text;
        this.cadenceMode = cadenceMode == null ? StepCadenceMode.ALWAYS : cadenceMode;
        this.weekdayMask = this.cadenceMode == StepCadenceMode.WEEKDAYS
                ? weekdayMask & 0x7f : 0;
        this.intervalDays = this.cadenceMode == StepCadenceMode.INTERVAL ? intervalDays : null;
        this.prescription = Objects.requireNonNull(prescription, "prescription");
        if (assistantPolicy != null && prescription.training == null)
            throw new IllegalArgumentException("An assistant policy needs training values");
        this.assistantPolicy = assistantPolicy;
        this.assistantState = assistantPolicy == null ? TrainingAssistantState.disabled()
                : Objects.requireNonNull(assistantState, "assistantState");
        this.amount = prescription.amount;
        this.restTimerPolicy = prescription.rest;
        this.trainingAssistant = legacyTrainingConfig(prescription, assistantPolicy);
        this.note = note == null ? "" : note;
        this.activationKind = activationKind == null
                ? StepActivationKind.SCHEDULED : activationKind;
    }

    public static EditorStepState blank(int identity) {
        return new EditorStepState(DRAFT_PREFIX + identity, "", StepCadenceMode.ALWAYS, 0, null,
                StepPrescription.forAmount(StepAmount.none()), null, "",
                StepActivationKind.SCHEDULED);
    }

    public static EditorStepState from(TaskStepTemplate value) {
        StepCadenceMode cadence = value.weekdayMask != 0 ? StepCadenceMode.WEEKDAYS
                : value.intervalDays != 0 ? StepCadenceMode.INTERVAL : StepCadenceMode.ALWAYS;
        return new EditorStepState(value.id, value.text, cadence, value.weekdayMask,
                value.intervalDays == 0 ? null : value.intervalDays, value.prescription,
                value.assistantProfile == null ? null : value.assistantProfile.policy,
                value.assistantProfile == null ? TrainingAssistantState.disabled()
                        : value.assistantProfile.state,
                value.note, value.activationKind);
    }

    static EditorStepState fromStored(String id, String text, StepCadenceMode cadenceMode,
                                      int weekdayMask, Integer intervalDays, StepAmount amount,
                                      RestTimerPolicy rest, TrainingAssistantConfig assistant,
                                      TrainingAssistantState assistantState,
                                      String note, StepActivationKind activationKind) {
        TrainingAssistantConfig resolved = assistant == null
                ? TrainingAssistantConfig.disabled() : assistant;
        boolean sets = amount instanceof StepAmount.SetsReps;
        StepPrescription prescription = StepPrescription.restore(amount,
                sets ? rest : RestTimerPolicy.off(),
                sets && resolved.enabled ? resolved.load : ResistanceLoad.unspecified(),
                resolved.targetRir);
        return new EditorStepState(id, text, cadenceMode, weekdayMask, intervalDays,
                prescription, sets ? policy(resolved) : null,
                sets && resolved.enabled ? assistantState : TrainingAssistantState.disabled(),
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
                prescription, assistantPolicy, note, resolved);
    }

    private static TrainingAssistantPolicy policy(TrainingAssistantConfig value) {
        return value.enabled ? new TrainingAssistantPolicy(value.minSets, value.maxSets,
                value.minRepetitions, value.maxRepetitions,
                value.automaticWeeklySetCeiling, value.primaryMuscle,
                value.secondaryMuscles) : null;
    }

    private static TrainingAssistantConfig legacyTrainingConfig(
            StepPrescription prescription, TrainingAssistantPolicy policy) {
        if (policy == null) return TrainingAssistantConfig.disabled();
        return new TrainingAssistantConfig(true, policy.minSets, policy.maxSets,
                policy.minRepetitions, policy.maxRepetitions,
                prescription.training.targetRir,
                policy.automaticWeeklySetCeiling, prescription.training.load,
                policy.primaryMuscle, policy.secondaryMuscles);
    }

    public EditorStepState withText(String value) {
        return copy(value, cadenceMode, weekdayMask, intervalDays, prescription,
                assistantPolicy, note);
    }

    public EditorStepState withWeekdayMask(int value) {
        return copy(text, StepCadenceMode.WEEKDAYS, value, null, prescription,
                assistantPolicy, note);
    }

    public EditorStepState withIntervalDays(Integer value) {
        return copy(text, StepCadenceMode.INTERVAL, 0, value, prescription,
                assistantPolicy, note);
    }

    public EditorStepState withCadenceMode(StepCadenceMode value) {
        if (value == StepCadenceMode.WEEKDAYS)
            return copy(text, value, weekdayMask == 0 ? 1 : weekdayMask, null, prescription,
                    assistantPolicy, note);
        if (value == StepCadenceMode.INTERVAL)
            return copy(text, value, 0, intervalDays == null ? 2 : intervalDays, prescription,
                    assistantPolicy, note);
        return copy(text, StepCadenceMode.ALWAYS, 0, null, prescription,
                assistantPolicy, note);
    }

    public EditorStepState withAmount(StepAmount value) {
        RestTimerPolicy rest = value instanceof StepAmount.SetsReps
                ? amount instanceof StepAmount.SetsReps ? restTimerPolicy
                : RestTimerPolicy.inherit() : RestTimerPolicy.off();
        boolean retainsTraining = value instanceof StepAmount.SetsReps
                && amount instanceof StepAmount.SetsReps;
        return copy(text, cadenceMode, weekdayMask, intervalDays,
                new StepPrescription(value, rest,
                        retainsTraining ? prescription.training : null),
                retainsTraining ? assistantPolicy : null, note);
    }

    public EditorStepState withRestTimerPolicy(RestTimerPolicy value) {
        return copy(text, cadenceMode, weekdayMask, intervalDays,
                new StepPrescription(amount, value, prescription.training), assistantPolicy,
                note);
    }

    public EditorStepState withTrainingAssistant(TrainingAssistantConfig value) {
        TrainingAssistantConfig resolved = value == null
                ? TrainingAssistantConfig.disabled() : value;
        TrainingAssistantPolicy nextPolicy = policy(resolved);
        TrainingAssistantState nextState = nextPolicy == null
                ? TrainingAssistantState.disabled()
                : assistantPolicy == null ? TrainingAssistantState.calibrating() : assistantState;
        return copy(text, cadenceMode, weekdayMask, intervalDays,
                StepPrescription.restore(amount, restTimerPolicy, resolved.load,
                        resolved.targetRir), nextPolicy, nextState, note);
    }

    public EditorStepState withNote(String value) {
        return copy(text, cadenceMode, weekdayMask, intervalDays, prescription,
                assistantPolicy, value);
    }

    private EditorStepState copy(String newText, StepCadenceMode cadence, int weekdays,
                                 Integer interval, StepPrescription newPrescription,
                                 TrainingAssistantPolicy policy, String newNote) {
        return copy(newText, cadence, weekdays, interval, newPrescription, policy,
                policy == null ? TrainingAssistantState.disabled() : assistantState, newNote);
    }

    private EditorStepState copy(String newText, StepCadenceMode cadence, int weekdays,
                                 Integer interval, StepPrescription newPrescription,
                                 TrainingAssistantPolicy policy, TrainingAssistantState state,
                                 String newNote) {
        return new EditorStepState(id, newText, cadence, weekdays, interval, newPrescription,
                policy, state, newNote, activationKind);
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
                && Objects.equals(assistantPolicy, value.assistantPolicy)
                && assistantState.equals(value.assistantState)
                && note.equals(value.note) && activationKind == value.activationKind;
    }

    @Override public int hashCode() {
        return Objects.hash(id, text, cadenceMode, weekdayMask, intervalDays, prescription,
                assistantPolicy, assistantState, note, activationKind);
    }
}
