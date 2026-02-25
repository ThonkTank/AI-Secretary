package com.autosecretary.features.task.ui.internal.editor;

import android.widget.CheckBox;
import android.widget.EditText;

public class TaskEditFormValidator {

    public boolean validateAndCollectAllFields(
        EditText titleView,
        EditText minDurationView,
        EditText maxDurationView,
        EditText cooldownView,
        CheckBox toggleRepetition,
        EditText repsView,
        EditText perPeriodView,
        CheckBox toggleProgress,
        EditText targetView,
        EditText currentView,
        EditText minPerRepView,
        EditText maxPerRepView
    ) {
        clearFieldErrors(
            titleView,
            minDurationView,
            maxDurationView,
            cooldownView,
            repsView,
            perPeriodView,
            targetView,
            currentView,
            minPerRepView,
            maxPerRepView
        );

        boolean valid = true;
        valid &= requireNonEmpty(titleView, "Titel ist erforderlich.");

        valid &= validateIntegerField(minDurationView, 1, Integer.MAX_VALUE,
            "Minimale Dauer muss mindestens 1 Minute sein.");
        valid &= validateIntegerField(maxDurationView, 1, Integer.MAX_VALUE,
            "Maximale Dauer muss mindestens 1 Minute sein.");
        valid &= validateIntegerField(cooldownView, 0, Integer.MAX_VALUE,
            "Cooldown muss mindestens 0 Tage sein.");

        if (toggleRepetition.isChecked()) {
            valid &= validateIntegerField(repsView, 1, Integer.MAX_VALUE,
                "Wiederholungen müssen mindestens 1 sein.");
            valid &= validateIntegerField(perPeriodView, 1, Integer.MAX_VALUE,
                "Intervall muss mindestens 1 sein.");
        }

        if (toggleProgress.isChecked()) {
            valid &= validateIntegerField(targetView, 1, Integer.MAX_VALUE,
                "Ziel muss mindestens 1 sein.");
            valid &= validateIntegerField(currentView, 0, Integer.MAX_VALUE,
                "Aktueller Wert muss mindestens 0 sein.");
            valid &= validateIntegerField(minPerRepView, 0, Integer.MAX_VALUE,
                "Minimum pro Wiederholung muss mindestens 0 sein.");
            valid &= validateIntegerField(maxPerRepView, 0, Integer.MAX_VALUE,
                "Maximum pro Wiederholung muss mindestens 0 sein.");
        }

        if (valid) {
            valid &= validateMinMaxPair(
                minDurationView,
                maxDurationView,
                "Minimale Dauer darf nicht größer als maximale Dauer sein.",
                "Maximale Dauer muss mindestens so groß wie minimale Dauer sein."
            );
        }

        if (valid && toggleProgress.isChecked()) {
            valid &= validateMinMaxPair(
                minPerRepView,
                maxPerRepView,
                "Minimum pro Wiederholung darf nicht größer als Maximum sein.",
                "Maximum pro Wiederholung muss mindestens so groß wie das Minimum sein."
            );
            valid &= validateCurrentNotAboveTarget(currentView, targetView);
        }

        return valid;
    }

    private void clearFieldErrors(
        EditText titleView,
        EditText minDurationView,
        EditText maxDurationView,
        EditText cooldownView,
        EditText repsView,
        EditText perPeriodView,
        EditText targetView,
        EditText currentView,
        EditText minPerRepView,
        EditText maxPerRepView
    ) {
        titleView.setError(null);
        minDurationView.setError(null);
        maxDurationView.setError(null);
        cooldownView.setError(null);
        repsView.setError(null);
        perPeriodView.setError(null);
        targetView.setError(null);
        currentView.setError(null);
        minPerRepView.setError(null);
        maxPerRepView.setError(null);
    }

    private boolean requireNonEmpty(EditText field, String message) {
        if (field.getText() == null || field.getText().toString().trim().isEmpty()) {
            field.setError(message);
            return false;
        }
        return true;
    }

    private boolean validateIntegerField(EditText field, int min, int max, String rangeMessage) {
        String value = field.getText() != null ? field.getText().toString().trim() : "";
        if (value.isEmpty()) {
            field.setError("Pflichtfeld. " + rangeMessage);
            return false;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                field.setError(rangeMessage);
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            field.setError("Bitte eine ganze Zahl eingeben. " + rangeMessage);
            return false;
        }
    }

    private boolean validateMinMaxPair(EditText minField, EditText maxField,
                                       String minMessage, String maxMessage) {
        int minValue = Integer.parseInt(minField.getText().toString().trim());
        int maxValue = Integer.parseInt(maxField.getText().toString().trim());
        if (minValue <= maxValue) {
            return true;
        }
        minField.setError(minMessage);
        maxField.setError(maxMessage);
        return false;
    }

    private boolean validateCurrentNotAboveTarget(EditText currentView, EditText targetView) {
        int current = Integer.parseInt(currentView.getText().toString().trim());
        int target = Integer.parseInt(targetView.getText().toString().trim());
        if (current <= target) {
            return true;
        }
        currentView.setError("Aktuell darf nicht größer als Ziel sein.");
        targetView.setError("Ziel muss mindestens so groß wie Aktuell sein.");
        return false;
    }
}
