package com.autosecretary.features.meal.ui.internal;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.autosecretary.R;
import com.autosecretary.features.meal.domain.CookingPreferences;
import com.autosecretary.shared.ui.DialogHelper;
import com.autosecretary.shared.ui.DialogValidation;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Manages the cooking preferences dialog. Shows per-MealType max cooking sessions
 * and the quick-prep threshold.
 */
public class MealCookingPrefsDialogController {

    public interface Listener {
        void onPrefsSaved(CookingPreferences prefs);
    }

    private final Context context;
    private final Listener listener;

    public MealCookingPrefsDialogController(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    /** Opens the dialog pre-filled with current preferences. */
    public void show(CookingPreferences current) {
        Context ctx = context;
        View content = LayoutInflater.from(ctx).inflate(R.layout.meal_cooking_prefs_dialog, null);

        TextInputEditText breakfastMax = content.findViewById(R.id.MealCookingPrefsBreakfastMax);
        TextInputEditText lunchMax = content.findViewById(R.id.MealCookingPrefsLunchMax);
        TextInputEditText dinnerMax = content.findViewById(R.id.MealCookingPrefsDinnerMax);
        TextInputEditText snackMax = content.findViewById(R.id.MealCookingPrefsSnackMax);
        TextInputEditText quickPrep = content.findViewById(R.id.MealCookingPrefsQuickPrep);

        breakfastMax.setText(String.valueOf(current.maxBreakfastCooking));
        lunchMax.setText(String.valueOf(current.maxLunchCooking));
        dinnerMax.setText(String.valueOf(current.maxDinnerCooking));
        snackMax.setText(String.valueOf(current.maxSnackCooking));
        quickPrep.setText(String.valueOf(current.quickPrepMaxMinutes));

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.meal_cooking_prefs_dialog_title)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null)
                .create();

        DialogHelper.showWithValidation(dialog, () -> {
            int bMax = DialogValidation.parseIntOrDefault(DialogValidation.textOf(breakfastMax), 3);
            int lMax = DialogValidation.parseIntOrDefault(DialogValidation.textOf(lunchMax), 3);
            int dMax = DialogValidation.parseIntOrDefault(DialogValidation.textOf(dinnerMax), 3);
            int sMax = DialogValidation.parseIntOrDefault(DialogValidation.textOf(snackMax), 0);
            int qp = DialogValidation.parseIntOrDefault(DialogValidation.textOf(quickPrep), 15);

            // Work on a copy so that cancellation (or future retries) does not mutate the
            // caller's original CookingPreferences object.
            CookingPreferences saved = new CookingPreferences();
            saved.id = current.id;
            saved.maxBreakfastCooking = Math.max(0, bMax);
            saved.maxLunchCooking = Math.max(0, lMax);
            saved.maxDinnerCooking = Math.max(0, dMax);
            saved.maxSnackCooking = Math.max(0, sMax);
            saved.quickPrepMaxMinutes = Math.max(0, qp);
            saved.breakfastCookingDays = current.breakfastCookingDays;
            saved.lunchCookingDays = current.lunchCookingDays;
            saved.dinnerCookingDays = current.dinnerCookingDays;
            saved.snackCookingDays = current.snackCookingDays;

            listener.onPrefsSaved(saved);
            dialog.dismiss();
        });
    }
}
