package com.autosecretary.features.task.ui.edit.internal.editor;

import android.widget.EditText;

import com.autosecretary.features.task.ui.edit.TaskEditFormViews;

public class TaskEditFormValidator {

    public boolean validate(TaskEditFormViews views) {
        clearErrors(views);

        boolean valid = true;
        valid &= requireNonEmpty(views.titleView, "Titel ist erforderlich.");

        valid &= validateIntegerField(views.minDurationView, 1, Integer.MAX_VALUE,
            "Minimale Dauer muss mindestens 1 Minute sein.");
        valid &= validateIntegerField(views.maxDurationView, 1, Integer.MAX_VALUE,
            "Maximale Dauer muss mindestens 1 Minute sein.");
        valid &= validateIntegerField(views.cooldownView, 0, Integer.MAX_VALUE,
            "Cooldown muss mindestens 0 Tage sein.");
        valid &= validateLongField(views.budgetRequirementCentsView, 0L, Long.MAX_VALUE,
            "Budgetbedarf muss mindestens 0 Cent sein.");

        if (views.toggleRepetition.isChecked()) {
            valid &= validateIntegerField(views.repsView, 1, Integer.MAX_VALUE,
                "Wiederholungen müssen mindestens 1 sein.");
            valid &= validateIntegerField(views.perPeriodView, 1, Integer.MAX_VALUE,
                "Intervall muss mindestens 1 sein.");
        }

        if (views.toggleProgress.isChecked()) {
            valid &= validateIntegerField(views.targetView, 1, Integer.MAX_VALUE,
                "Ziel muss mindestens 1 sein.");
            valid &= validateIntegerField(views.currentView, 0, Integer.MAX_VALUE,
                "Aktueller Wert muss mindestens 0 sein.");
            valid &= validateIntegerField(views.minPerRepView, 0, Integer.MAX_VALUE,
                "Minimum pro Wiederholung muss mindestens 0 sein.");
            valid &= validateIntegerField(views.maxPerRepView, 0, Integer.MAX_VALUE,
                "Maximum pro Wiederholung muss mindestens 0 sein.");
        }

        if (valid) {
            valid &= validateMinMaxPair(
                views.minDurationView,
                views.maxDurationView,
                "Minimale Dauer darf nicht größer als maximale Dauer sein.",
                "Maximale Dauer muss mindestens so groß wie minimale Dauer sein."
            );
        }

        if (valid && views.toggleProgress.isChecked()) {
            valid &= validateMinMaxPair(
                views.minPerRepView,
                views.maxPerRepView,
                "Minimum pro Wiederholung darf nicht größer als Maximum sein.",
                "Maximum pro Wiederholung muss mindestens so groß wie das Minimum sein."
            );
            valid &= validateCurrentNotAboveTarget(views.currentView, views.targetView);
        }

        return valid;
    }

    private void clearErrors(TaskEditFormViews views) {
        views.titleView.setError(null);
        views.minDurationView.setError(null);
        views.maxDurationView.setError(null);
        views.cooldownView.setError(null);
        views.budgetRequirementCentsView.setError(null);
        views.repsView.setError(null);
        views.perPeriodView.setError(null);
        views.targetView.setError(null);
        views.currentView.setError(null);
        views.minPerRepView.setError(null);
        views.maxPerRepView.setError(null);
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


    private boolean validateLongField(EditText field, long min, long max, String rangeMessage) {
        String value = field.getText() != null ? field.getText().toString().trim() : "";
        if (value.isEmpty()) {
            field.setError("Pflichtfeld. " + rangeMessage);
            return false;
        }
        try {
            long parsed = Long.parseLong(value);
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
