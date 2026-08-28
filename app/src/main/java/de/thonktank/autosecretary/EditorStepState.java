package de.thonktank.autosecretary;

import android.os.Bundle;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.RestTimerPolicy;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAssistantConfig;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;

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
    public final String note;
    public final StepActivationKind activationKind;

    public EditorStepState(String id, String text, int weekdayMask, StepAmount amount,
                           String note) {
        this(id, text, weekdayMask, 0, amount, note);
    }

    public EditorStepState(String id, String text, int weekdayMask, int intervalDays,
                           StepAmount amount, String note) {
        this(id, text, weekdayMask, intervalDays, amount,
                RestTimerPolicy.forAmount(amount), TrainingAssistantConfig.disabled(), note,
                StepActivationKind.SCHEDULED);
    }

    public EditorStepState(String id, String text, int weekdayMask, int intervalDays,
                           StepAmount amount, RestTimerPolicy restTimerPolicy, String note) {
        this(id, text, weekdayMask, intervalDays, amount, restTimerPolicy,
                TrainingAssistantConfig.disabled(), note,
                StepActivationKind.SCHEDULED);
    }

    public EditorStepState(String id, String text, int weekdayMask, int intervalDays,
                           StepAmount amount, String note, StepActivationKind activationKind) {
        this(id, text, weekdayMask, intervalDays, amount, RestTimerPolicy.forAmount(amount),
                TrainingAssistantConfig.disabled(), note, activationKind);
    }

    public EditorStepState(String id, String text, int weekdayMask, int intervalDays,
                           StepAmount amount, RestTimerPolicy restTimerPolicy, String note,
                           StepActivationKind activationKind) {
        this(id, text, weekdayMask, intervalDays, amount, restTimerPolicy,
                TrainingAssistantConfig.disabled(), note, activationKind);
    }

    public EditorStepState(String id, String text, int weekdayMask, int intervalDays,
                           StepAmount amount, RestTimerPolicy restTimerPolicy,
                           TrainingAssistantConfig trainingAssistant, String note) {
        this(id, text, weekdayMask, intervalDays, amount, restTimerPolicy, trainingAssistant,
                note, StepActivationKind.SCHEDULED);
    }

    public EditorStepState(String id, String text, int weekdayMask, int intervalDays,
                           StepAmount amount, RestTimerPolicy restTimerPolicy,
                           TrainingAssistantConfig trainingAssistant, String note,
                           StepActivationKind activationKind) {
        this(id, text, weekdayMask != 0 ? StepCadenceMode.WEEKDAYS
                        : intervalDays != 0 ? StepCadenceMode.INTERVAL : StepCadenceMode.ALWAYS,
                weekdayMask, intervalDays == 0 ? null : intervalDays, amount,
                restTimerPolicy, trainingAssistant, note, activationKind);
    }

    public EditorStepState(String id, String text, StepCadenceMode cadenceMode,
                           int weekdayMask, Integer intervalDays, StepAmount amount, String note) {
        this(id, text, cadenceMode, weekdayMask, intervalDays, amount,
                RestTimerPolicy.forAmount(amount), TrainingAssistantConfig.disabled(), note,
                StepActivationKind.SCHEDULED);
    }

    public EditorStepState(String id, String text, StepCadenceMode cadenceMode,
                           int weekdayMask, Integer intervalDays, StepAmount amount,
                           RestTimerPolicy restTimerPolicy, String note) {
        this(id, text, cadenceMode, weekdayMask, intervalDays, amount, restTimerPolicy,
                TrainingAssistantConfig.disabled(), note, StepActivationKind.SCHEDULED);
    }

    public EditorStepState(String id, String text, StepCadenceMode cadenceMode,
                           int weekdayMask, Integer intervalDays, StepAmount amount,
                           RestTimerPolicy restTimerPolicy, String note,
                           StepActivationKind activationKind) {
        this(id, text, cadenceMode, weekdayMask, intervalDays, amount, restTimerPolicy,
                TrainingAssistantConfig.disabled(), note, activationKind);
    }

    public EditorStepState(String id, String text, StepCadenceMode cadenceMode,
                           int weekdayMask, Integer intervalDays, StepAmount amount,
                           RestTimerPolicy restTimerPolicy,
                           TrainingAssistantConfig trainingAssistant, String note) {
        this(id, text, cadenceMode, weekdayMask, intervalDays, amount, restTimerPolicy,
                trainingAssistant, note, StepActivationKind.SCHEDULED);
    }

    public EditorStepState(String id, String text, StepCadenceMode cadenceMode,
                           int weekdayMask, Integer intervalDays, StepAmount amount,
                           RestTimerPolicy restTimerPolicy,
                           TrainingAssistantConfig trainingAssistant, String note,
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
        this.trainingAssistant = this.amount instanceof StepAmount.SetsReps
                ? trainingAssistant == null ? TrainingAssistantConfig.disabled()
                : trainingAssistant : TrainingAssistantConfig.disabled();
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
                value.amount, value.restTimerPolicy, value.trainingAssistant, value.note,
                value.activationKind);
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
                amount, restTimerPolicy, trainingAssistant, note, resolved);
    }

    public EditorStepState withText(String value) {
        return copy(value, cadenceMode, weekdayMask, intervalDays, amount, restTimerPolicy,
                trainingAssistant, note);
    }

    public EditorStepState withWeekdayMask(int value) {
        return copy(text, StepCadenceMode.WEEKDAYS, value, null, amount, restTimerPolicy,
                trainingAssistant, note);
    }

    public EditorStepState withIntervalDays(Integer value) {
        return copy(text, StepCadenceMode.INTERVAL, 0, value, amount, restTimerPolicy,
                trainingAssistant, note);
    }

    public EditorStepState withCadenceMode(StepCadenceMode value) {
        if (value == StepCadenceMode.WEEKDAYS)
            return copy(text, value, weekdayMask == 0 ? 1 : weekdayMask, null, amount,
                    restTimerPolicy, trainingAssistant, note);
        if (value == StepCadenceMode.INTERVAL)
            return copy(text, value, 0, intervalDays == null ? 2 : intervalDays, amount,
                    restTimerPolicy, trainingAssistant, note);
        return copy(text, StepCadenceMode.ALWAYS, 0, null, amount, restTimerPolicy,
                trainingAssistant, note);
    }

    public EditorStepState withAmount(StepAmount value) {
        RestTimerPolicy rest = value instanceof StepAmount.SetsReps
                ? amount instanceof StepAmount.SetsReps ? restTimerPolicy
                : RestTimerPolicy.inherit() : RestTimerPolicy.off();
        TrainingAssistantConfig training = value instanceof StepAmount.SetsReps
                ? trainingAssistant : TrainingAssistantConfig.disabled();
        return copy(text, cadenceMode, weekdayMask, intervalDays, value, rest, training, note);
    }

    public EditorStepState withRestTimerPolicy(RestTimerPolicy value) {
        return copy(text, cadenceMode, weekdayMask, intervalDays, amount, value,
                trainingAssistant, note);
    }

    public EditorStepState withTrainingAssistant(TrainingAssistantConfig value) {
        return copy(text, cadenceMode, weekdayMask, intervalDays, amount, restTimerPolicy,
                value, note);
    }

    public EditorStepState withNote(String value) {
        return copy(text, cadenceMode, weekdayMask, intervalDays, amount, restTimerPolicy,
                trainingAssistant, value);
    }

    private EditorStepState copy(String newText, StepCadenceMode cadence, int weekdays,
                                 Integer interval, StepAmount newAmount, RestTimerPolicy rest,
                                 TrainingAssistantConfig training, String newNote) {
        return new EditorStepState(id, newText, cadence, weekdays, interval, newAmount, rest,
                training, newNote, activationKind);
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
            putInteger(bundle, "sets", value.sets); putInteger(bundle, "reps", value.repetitions);
        } else if (amount instanceof StepAmount.Repetitions) {
            putInteger(bundle, "reps", ((StepAmount.Repetitions) amount).repetitions);
        } else if (amount instanceof StepAmount.Duration) {
            putInteger(bundle, "duration", ((StepAmount.Duration) amount).seconds);
        }
        putTraining(bundle, trainingAssistant);
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
                training(bundle), bundle.getString("note", ""),
                BundleValues.enumValue(StepActivationKind.class, bundle.getString("activation"),
                        StepActivationKind.SCHEDULED));
    }

    private static void putTraining(Bundle bundle, TrainingAssistantConfig value) {
        bundle.putBoolean("training_enabled", value.enabled);
        bundle.putInt("training_min_sets", value.minSets);
        bundle.putInt("training_max_sets", value.maxSets);
        bundle.putInt("training_min_reps", value.minRepetitions);
        bundle.putInt("training_max_reps", value.maxRepetitions);
        bundle.putInt("training_target_rir", value.targetRir);
        bundle.putLong("training_increment", value.loadIncrementMilli);
        bundle.putInt("training_weekly_ceiling", value.automaticWeeklySetCeiling);
        bundle.putString("training_load_mode", value.load.mode.name());
        bundle.putString("training_load_unit", value.load.unit.name());
        if (value.load.milliUnits != null) {
            bundle.putBoolean("training_load_set", true);
            bundle.putLong("training_load", value.load.milliUnits);
        }
        if (value.primaryMuscle != null)
            bundle.putString("training_primary", value.primaryMuscle.name());
        StringBuilder muscles = new StringBuilder();
        for (TrainingMuscleGroup muscle : value.secondaryMuscles) {
            if (muscles.length() > 0) muscles.append(',');
            muscles.append(muscle.name());
        }
        bundle.putString("training_secondaries", muscles.toString());
    }

    private static TrainingAssistantConfig training(Bundle bundle) {
        if (!bundle.getBoolean("training_enabled")) return TrainingAssistantConfig.disabled();
        ResistanceLoad load = ResistanceLoad.restore(bundle.getString("training_load_mode"),
                bundle.getString("training_load_unit"), bundle.getBoolean("training_load_set")
                        ? bundle.getLong("training_load") : null);
        Set<TrainingMuscleGroup> secondary = EnumSet.noneOf(TrainingMuscleGroup.class);
        for (String value : bundle.getString("training_secondaries", "").split(",")) {
            TrainingMuscleGroup muscle = muscle(value);
            if (muscle != null) secondary.add(muscle);
        }
        return new TrainingAssistantConfig(true,
                positive(bundle.getInt("training_min_sets"), 2),
                positive(bundle.getInt("training_max_sets"), 3),
                positive(bundle.getInt("training_min_reps"), 8),
                positive(bundle.getInt("training_max_reps"), 12),
                bundle.containsKey("training_target_rir") ? bundle.getInt("training_target_rir") : 2,
                bundle.containsKey("training_increment") ? bundle.getLong("training_increment") : 2_500,
                positive(bundle.getInt("training_weekly_ceiling"), 10), load,
                muscle(bundle.getString("training_primary")), secondary);
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

    private static TrainingMuscleGroup muscle(String value) {
        try { return value == null || value.isEmpty() ? null : TrainingMuscleGroup.valueOf(value); }
        catch (IllegalArgumentException error) { return null; }
    }

    private static int positive(int value, int fallback) { return value > 0 ? value : fallback; }
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
                && Objects.equals(intervalDays, value.intervalDays) && amount.equals(value.amount)
                && restTimerPolicy.equals(value.restTimerPolicy)
                && trainingAssistant.equals(value.trainingAssistant)
                && note.equals(value.note) && activationKind == value.activationKind;
    }

    @Override public int hashCode() {
        return Objects.hash(id, text, cadenceMode, weekdayMask, intervalDays, amount,
                restTimerPolicy, trainingAssistant, note, activationKind);
    }
}
