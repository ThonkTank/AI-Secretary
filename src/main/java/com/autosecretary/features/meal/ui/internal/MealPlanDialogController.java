package com.autosecretary.features.meal.ui.internal;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.features.meal.domain.MealType;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.shared.ui.DialogHelper;
import com.autosecretary.shared.ui.DialogValidation;
import com.autosecretary.shared.ui.SpinnerHelper;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Manages the "plan a recipe" dialog. Loads recipes into a spinner, validates input,
 * and notifies the listener on successful submission.
 */
public class MealPlanDialogController {

    private static final String DEFAULT_SERVINGS = "2";

    public interface Listener {
        void onPlanSubmitted(String recipeId, LocalDate date, MealType mealType, int servings);
    }

    private final Fragment fragment;
    private final Listener listener;

    public MealPlanDialogController(Fragment fragment, Listener listener) {
        this.fragment = fragment;
        this.listener = listener;
    }

    public void show(List<Recipe> recipes) {
        Context ctx = fragment.requireContext();
        if (recipes == null || recipes.isEmpty()) {
            Toast.makeText(ctx, R.string.meal_error_no_recipes, Toast.LENGTH_SHORT).show();
            return;
        }

        View content = LayoutInflater.from(ctx).inflate(R.layout.meal_plan_create_dialog, null);
        Spinner recipeSpinner = content.findViewById(R.id.MealDialogRecipe);
        TextInputEditText dateField = content.findViewById(R.id.MealDialogDate);
        Spinner typeSpinner = content.findViewById(R.id.MealDialogType);
        TextInputEditText servingsField = content.findViewById(R.id.MealDialogServings);

        SpinnerHelper.bindList(recipeSpinner, recipes, r -> r.title, ctx);
        recipeSpinner.setSelection(0);
        dateField.setText(LocalDate.now().toString());
        DialogHelper.setupDatePicker(dateField, ctx);
        SpinnerHelper.bindList(typeSpinner, Arrays.asList(MealType.values()), mt -> mt.label, ctx);
        typeSpinner.setSelection(0);
        servingsField.setText(DEFAULT_SERVINGS);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.meal_plan_dialog_title)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.meal_plan_dialog_save, null)
                .create();
        DialogHelper.showWithValidation(dialog, () -> {
            int recipeIndex = recipeSpinner.getSelectedItemPosition();
            if (recipeIndex < 0 || recipeIndex >= recipes.size()) return;
            LocalDate parsedDate = DialogValidation.parseDate(dateField, ctx);
            if (parsedDate == null) return;
            Integer servings = DialogValidation.parseInt(servingsField,
                    ctx.getString(R.string.meal_field_servings), ctx);
            if (servings == null || servings <= 0) return;
            MealType mealType = SpinnerHelper.enumAtPosition(typeSpinner, MealType.values());
            if (mealType == null) return;
            String recipeId = recipes.get(recipeIndex).id;
            if (recipeId == null) return;
            listener.onPlanSubmitted(recipeId, parsedDate, mealType, servings);
            dialog.dismiss();
        });
    }
}
