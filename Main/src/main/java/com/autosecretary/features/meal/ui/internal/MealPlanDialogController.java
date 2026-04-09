package com.autosecretary.features.meal.ui.internal;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.features.meal.domain.MealType;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeScalingResult;
import com.autosecretary.features.meal.domain.RecipeScalingService;
import com.autosecretary.shared.ui.DialogHelper;
import com.autosecretary.shared.ui.DialogValidation;
import com.autosecretary.shared.ui.SpinnerHelper;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Manages the "plan a recipe" dialog. Loads recipes into a spinner, validates input,
 * and notifies the listener on successful submission. Shows a scaled ingredient preview
 * when a recipe with ingredients is selected.
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
        TextView scaledLabel = content.findViewById(R.id.MealDialogScaledLabel);
        LinearLayout scaledContainer = content.findViewById(R.id.MealDialogScaledIngredients);

        SpinnerHelper.bindList(recipeSpinner, recipes, r -> r.title, ctx);
        recipeSpinner.setSelection(0);
        dateField.setText(LocalDate.now().toString());
        DialogHelper.setupDatePicker(dateField, ctx);
        SpinnerHelper.bindList(typeSpinner, Arrays.asList(MealType.values()), mt -> mt.label, ctx);
        typeSpinner.setSelection(0);
        servingsField.setText(DEFAULT_SERVINGS);

        // Update scaling preview when recipe or servings change
        Runnable updatePreview = () -> updateScalingPreview(ctx, recipes, recipeSpinner,
                servingsField, scaledLabel, scaledContainer);

        recipeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updatePreview.run();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        servingsField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updatePreview.run();
            }
        });

        // Initial preview
        updatePreview.run();

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

    private void updateScalingPreview(Context ctx, List<Recipe> recipes,
                                       Spinner recipeSpinner, TextInputEditText servingsField,
                                       TextView scaledLabel, LinearLayout scaledContainer) {
        int idx = recipeSpinner.getSelectedItemPosition();
        if (idx < 0 || idx >= recipes.size()) {
            scaledLabel.setVisibility(View.GONE);
            scaledContainer.setVisibility(View.GONE);
            return;
        }
        Recipe recipe = recipes.get(idx);
        if (recipe.ingredients == null || recipe.ingredients.isEmpty()) {
            scaledLabel.setVisibility(View.GONE);
            scaledContainer.setVisibility(View.GONE);
            return;
        }

        int servings;
        try {
            servings = Integer.parseInt(servingsField.getText().toString().trim());
        } catch (NumberFormatException e) {
            scaledLabel.setVisibility(View.GONE);
            scaledContainer.setVisibility(View.GONE);
            return;
        }
        if (servings <= 0) return;

        RecipeScalingResult result = RecipeScalingService.scaleRecipe(recipe, servings);
        scaledLabel.setText(ctx.getString(R.string.meal_plan_scaled_label, servings));
        scaledLabel.setVisibility(View.VISIBLE);
        scaledContainer.removeAllViews();
        scaledContainer.setVisibility(View.VISIBLE);

        for (RecipeScalingResult.ScaledIngredient ing : result.ingredients()) {
            String amountStr = ing.amount() == (int) ing.amount()
                    ? String.valueOf((int) ing.amount())
                    : String.format("%.1f", ing.amount());
            TextView line = new TextView(ctx);
            line.setText(ctx.getString(R.string.meal_plan_scaled_ingredient_format,
                    ing.ingredientName(), amountStr, ing.unit()));
            scaledContainer.addView(line);
        }
    }
}
