package com.autosecretary.features.task.ui.internal.editor;

import android.widget.EditText;

public class TaskEditFormValidator {

    public boolean validateAndCollectAllFields(TaskEditFormViews formViews) {
        clearFieldErrors(formViews);

        boolean valid = true;
        valid &= requireNonEmpty(formViews.titleView, "Titel ist erforderlich.");

        valid &= validateIntegerField(formViews.minDurationView, 1, Integer.MAX_VALUE,
            "Minimale Dauer muss mindestens 1 Minute sein.");
        valid &= validateIntegerField(formViews.maxDurationView, 1, Integer.MAX_VALUE,
            "Maximale Dauer muss mindestens 1 Minute sein.");
        valid &= validateIntegerField(formViews.cooldownView, 0, Integer.MAX_VALUE,
            "Cooldown muss mindestens 0 Tage sein.");

        if (formViews.toggleRepetition.isChecked()) {
            valid &= validateIntegerField(formViews.repsView, 1, Integer.MAX_VALUE,
                "Wiederholungen müssen mindestens 1 sein.");
            valid &= validateIntegerField(formViews.perPeriodView, 1, Integer.MAX_VALUE,
                "Intervall muss mindestens 1 sein.");
        }

        if (formViews.toggleProgress.isChecked()) {
            valid &= validateIntegerField(formViews.targetView, 1, Integer.MAX_VALUE,
                "Ziel muss mindestens 1 sein.");
            valid &= validateIntegerField(formViews.currentView, 0, Integer.MAX_VALUE,
                "Aktueller Wert muss mindestens 0 sein.");
            valid &= validateIntegerField(formViews.minPerRepView, 0, Integer.MAX_VALUE,
                "Minimum pro Wiederholung muss mindestens 0 sein.");
            valid &= validateIntegerField(formViews.maxPerRepView, 0, Integer.MAX_VALUE,
                "Maximum pro Wiederholung muss mindestens 0 sein.");
        }

        if (valid) {
            valid &= validateMinMaxPair(
                formViews.minDurationView,
                formViews.maxDurationView,
                "Minimale Dauer darf nicht größer als maximale Dauer sein.",
                "Maximale Dauer muss mindestens so groß wie minimale Dauer sein."
            );
        }

        if (valid && formViews.toggleProgress.isChecked()) {
            valid &= validateMinMaxPair(
                formViews.minPerRepView,
                formViews.maxPerRepView,
                "Minimum pro Wiederholung darf nicht größer als Maximum sein.",
                "Maximum pro Wiederholung muss mindestens so groß wie das Minimum sein."
            );
            valid &= validateCurrentNotAboveTarget(formViews);
        }

        return valid;
    }

    private void clearFieldErrors(TaskEditFormViews formViews) {
        formViews.titleView.setError(null);
        formViews.minDurationView.setError(null);
        formViews.maxDurationView.setError(null);
        formViews.cooldownView.setError(null);
        formViews.repsView.setError(null);
        formViews.perPeriodView.setError(null);
        formViews.targetView.setError(null);
        formViews.currentView.setError(null);
        formViews.minPerRepView.setError(null);
        formViews.maxPerRepView.setError(null);
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

    private boolean validateCurrentNotAboveTarget(TaskEditFormViews formViews) {
        int current = Integer.parseInt(formViews.currentView.getText().toString().trim());
        int target = Integer.parseInt(formViews.targetView.getText().toString().trim());
        if (current <= target) {
            return true;
        }
        formViews.currentView.setError("Aktuell darf nicht größer als Ziel sein.");
        formViews.targetView.setError("Ziel muss mindestens so groß wie Aktuell sein.");
        return false;
    }
}
