package de.thonktank.autosecretary;

import android.os.Bundle;

import java.util.EnumSet;
import java.util.Set;

import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.RestTimerPolicy;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.domain.model.TrainingAssistantConfig;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;

/** Stable Bundle boundary for one editor step; legacy keys remain readable. */
final class EditorStepSavedStateCodec {
    private EditorStepSavedStateCodec() { }

    static Bundle encode(EditorStepState step) {
        Bundle bundle = new Bundle();
        bundle.putString("id", step.id); bundle.putString("text", step.text);
        bundle.putString("cadence", step.cadenceMode.name());
        bundle.putInt("weekdays", step.weekdayMask); putInteger(bundle, "interval", step.intervalDays);
        bundle.putString("amount", step.prescription.amount.kind().name());
        bundle.putString("rest_timer_mode", step.prescription.rest.mode.name());
        putInteger(bundle, "rest_timer_seconds", step.prescription.rest.customSeconds);
        StepAmount amount = step.prescription.amount;
        if (amount instanceof StepAmount.SetsReps) {
            StepAmount.SetsReps value = (StepAmount.SetsReps) amount;
            putInteger(bundle, "sets", value.sets); putInteger(bundle, "reps", value.repetitions);
        } else if (amount instanceof StepAmount.Repetitions) {
            putInteger(bundle, "reps", ((StepAmount.Repetitions) amount).repetitions);
        } else if (amount instanceof StepAmount.Duration) {
            putInteger(bundle, "duration", ((StepAmount.Duration) amount).seconds);
        }
        putTraining(bundle, step.trainingAssistant);
        bundle.putString("training_state", step.assistantState.status.name());
        bundle.putInt("training_observations", step.assistantState.eligibleObservations);
        bundle.putInt("training_ready_streak", step.assistantState.readyStreak);
        bundle.putInt("training_hard_streak", step.assistantState.hardStreak);
        bundle.putString("note", step.note);
        bundle.putString("activation", step.activationKind.name());
        return bundle;
    }

    static EditorStepState decode(Bundle bundle) {
        int weekdays = bundle.getInt("weekdays");
        Integer interval = bundle.containsKey("cadence") ? integer(bundle, "interval")
                : bundle.getInt("interval") == 0 ? null : bundle.getInt("interval");
        StepCadenceMode cadence = cadence(bundle.getString("cadence"), weekdays, interval);
        StepAmount amount = StepAmount.fromStorage(enumValue(bundle.getString("amount")),
                integer(bundle, "sets"), integer(bundle, "reps"), integer(bundle, "duration"));
        return EditorStepState.fromStored(bundle.getString("id"), bundle.getString("text", ""),
                cadence, weekdays, interval, amount,
                RestTimerPolicy.fromStorage(bundle.getString("rest_timer_mode"),
                        integer(bundle, "rest_timer_seconds")),
                training(bundle), TrainingAssistantState.restore(
                        bundle.getString("training_state", "CALIBRATING"),
                        bundle.getInt("training_observations"),
                        bundle.getInt("training_ready_streak"),
                        bundle.getInt("training_hard_streak")), bundle.getString("note", ""),
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
}
