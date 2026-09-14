package de.thonktank.autosecretary;

import android.os.Bundle;

import java.util.EnumSet;
import java.util.Set;

import de.thonktank.autosecretary.domain.model.RestTimerPolicy;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.domain.model.StepPrescription;

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
        RestTimerPolicy rest = RestTimerPolicy.fromStorage(bundle.getString("rest_timer_mode"),
                integer(bundle, "rest_timer_seconds"));
        StepPrescription prescription = new StepPrescription(amount,
                amount instanceof StepAmount.SetsReps ? rest : RestTimerPolicy.off());
        return EditorStepState.fromStored(bundle.getString("id"), bundle.getString("text", ""),
                cadence, weekdays, interval, prescription,
                bundle.getString("note", ""),
                BundleValues.enumValue(StepActivationKind.class, bundle.getString("activation"),
                        StepActivationKind.SCHEDULED));
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



    private static int positive(int value, int fallback) { return value > 0 ? value : fallback; }
    private static void putInteger(Bundle bundle, String key, Integer value) {
        if (value != null) { bundle.putBoolean(key + "_set", true); bundle.putInt(key, value); }
    }
    private static Integer integer(Bundle bundle, String key) {
        return bundle.getBoolean(key + "_set") ? bundle.getInt(key) : null;
    }
}
