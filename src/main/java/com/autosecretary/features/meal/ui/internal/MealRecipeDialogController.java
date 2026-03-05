package com.autosecretary.features.meal.ui.internal;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.MealType;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.shared.ui.DialogHelper;
import com.autosecretary.shared.ui.DialogValidation;
import com.autosecretary.shared.ui.SpinnerHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Manages the recipe create/edit dialog. Supports adding dynamic ingredient rows
 * with autocomplete from the ingredient master. Validates title + servings as required.
 */
public class MealRecipeDialogController {

    private static final String[] EFFORT_LABELS = new String[3];

    public interface Listener {
        void onRecipeSaved(Recipe recipe);
        void onRecipeDeleted(String recipeId);
    }

    private final Fragment fragment;
    private final Listener listener;

    public MealRecipeDialogController(Fragment fragment, Listener listener) {
        this.fragment = fragment;
        this.listener = listener;
    }

    /** Opens a create dialog for a new recipe. */
    public void show(List<Ingredient> ingredients) {
        showInternal(null, ingredients);
    }

    /** Opens an edit dialog for an existing recipe. */
    public void showEdit(Recipe existing, List<Ingredient> ingredients) {
        showInternal(existing, ingredients);
    }

    private void showInternal(@Nullable Recipe existing, List<Ingredient> ingredients) {
        Context ctx = fragment.requireContext();
        boolean isEdit = existing != null;

        EFFORT_LABELS[0] = ctx.getString(R.string.meal_recipe_effort_quick);
        EFFORT_LABELS[1] = ctx.getString(R.string.meal_recipe_effort_medium);
        EFFORT_LABELS[2] = ctx.getString(R.string.meal_recipe_effort_elaborate);

        View content = LayoutInflater.from(ctx).inflate(R.layout.meal_recipe_edit_dialog, null);
        TextInputEditText titleField = content.findViewById(R.id.MealRecipeTitle);
        TextInputEditText descField = content.findViewById(R.id.MealRecipeDescription);
        TextInputEditText instrField = content.findViewById(R.id.MealRecipeInstructions);
        Spinner mealTypeSpinner = content.findViewById(R.id.MealRecipeMealType);
        TextInputEditText servingsField = content.findViewById(R.id.MealRecipeServings);
        TextInputEditText prepTimeField = content.findViewById(R.id.MealRecipePrepTime);
        TextInputEditText cookTimeField = content.findViewById(R.id.MealRecipeCookTime);
        Spinner effortSpinner = content.findViewById(R.id.MealRecipeEffort);
        TextInputEditText tagsField = content.findViewById(R.id.MealRecipeTags);
        CheckBox favoriteBox = content.findViewById(R.id.MealRecipeFavorite);
        TextInputEditText caloriesField = content.findViewById(R.id.MealRecipeCalories);
        TextInputEditText proteinField = content.findViewById(R.id.MealRecipeProtein);
        TextInputEditText carbsField = content.findViewById(R.id.MealRecipeCarbs);
        TextInputEditText fatField = content.findViewById(R.id.MealRecipeFat);
        LinearLayout ingredientContainer = content.findViewById(R.id.MealRecipeIngredientContainer);
        MaterialButton addIngredientBtn = content.findViewById(R.id.MealRecipeAddIngredient);

        // Populate meal type spinner
        SpinnerHelper.bindList(mealTypeSpinner, Arrays.asList(MealType.values()), mt -> mt.label, ctx);
        mealTypeSpinner.setSelection(0);

        // Populate effort spinner
        SpinnerHelper.bindList(effortSpinner, Arrays.asList(EFFORT_LABELS), s -> s, ctx);
        effortSpinner.setSelection(1); // default MEDIUM

        // Ingredient name autocomplete suggestions
        List<String> ingredientNames = new ArrayList<>();
        for (Ingredient ing : ingredients) ingredientNames.add(ing.name);

        // Add ingredient row handler
        addIngredientBtn.setOnClickListener(v -> addIngredientRow(ctx, ingredientContainer, ingredientNames, null));

        // Pre-fill for edit mode
        if (isEdit) {
            titleField.setText(existing.title);
            if (existing.description != null) descField.setText(existing.description);
            if (existing.instructions != null) instrField.setText(existing.instructions);
            if (existing.mealTypes != null && !existing.mealTypes.isEmpty()) {
                MealType first = existing.mealTypes.iterator().next();
                MealType[] values = MealType.values();
                for (int i = 0; i < values.length; i++) {
                    if (values[i] == first) { mealTypeSpinner.setSelection(i); break; }
                }
            }
            servingsField.setText(String.valueOf(existing.servings));
            if (existing.prepTimeMinutes > 0) prepTimeField.setText(String.valueOf(existing.prepTimeMinutes));
            if (existing.cookTimeMinutes > 0) cookTimeField.setText(String.valueOf(existing.cookTimeMinutes));
            if (existing.prepEffort != null) effortSpinner.setSelection(existing.prepEffort.ordinal());
            if (existing.tags != null) tagsField.setText(existing.tags);
            favoriteBox.setChecked(existing.isFavorite);
            if (existing.totalCalories > 0) caloriesField.setText(String.valueOf(existing.totalCalories));
            if (existing.totalProtein > 0) proteinField.setText(String.valueOf(existing.totalProtein));
            if (existing.totalCarbs > 0) carbsField.setText(String.valueOf(existing.totalCarbs));
            if (existing.totalFat > 0) fatField.setText(String.valueOf(existing.totalFat));
            if (existing.ingredients != null) {
                for (Recipe.RecipeIngredient ri : existing.ingredients) {
                    addIngredientRow(ctx, ingredientContainer, ingredientNames, ri);
                }
            }
        } else {
            servingsField.setText("2");
        }

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(isEdit ? R.string.meal_recipe_dialog_title_edit : R.string.meal_recipe_dialog_title_new)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null)
                .create();

        DialogHelper.showWithValidation(dialog, () -> {
            // Validate required fields
            String title = DialogValidation.requireNonEmpty(titleField,
                    ctx.getString(R.string.meal_field_title), ctx);
            if (title == null) return;
            Integer servings = DialogValidation.parseInt(servingsField,
                    ctx.getString(R.string.meal_field_servings), ctx);
            if (servings == null || servings <= 0) return;

            // Build recipe
            Recipe.Builder builder = new Recipe.Builder(title)
                    .description(DialogValidation.textOfNullable(descField))
                    .instructions(DialogValidation.textOfNullable(instrField))
                    .servings(servings)
                    .tags(DialogValidation.textOfNullable(tagsField));

            // Meal type
            MealType selectedType = SpinnerHelper.enumAtPosition(mealTypeSpinner, MealType.values());
            if (selectedType != null) builder.mealType(selectedType);

            // Times
            int prepTime = DialogValidation.parseIntOrDefault(DialogValidation.textOf(prepTimeField), 0);
            int cookTime = DialogValidation.parseIntOrDefault(DialogValidation.textOf(cookTimeField), 0);
            builder.prepTime(prepTime).cookTime(cookTime);

            // Effort
            int effortIdx = effortSpinner.getSelectedItemPosition();
            if (effortIdx >= 0 && effortIdx < Recipe.PrepEffort.values().length) {
                builder.effort(Recipe.PrepEffort.values()[effortIdx]);
            }

            // Favorite
            if (favoriteBox.isChecked()) builder.favorite();

            // Nutrition
            int cal = DialogValidation.parseIntOrDefault(DialogValidation.textOf(caloriesField), 0);
            int pro = DialogValidation.parseIntOrDefault(DialogValidation.textOf(proteinField), 0);
            int carbs = DialogValidation.parseIntOrDefault(DialogValidation.textOf(carbsField), 0);
            int fat = DialogValidation.parseIntOrDefault(DialogValidation.textOf(fatField), 0);

            // Ingredients from dynamic rows
            for (int i = 0; i < ingredientContainer.getChildCount(); i++) {
                View row = ingredientContainer.getChildAt(i);
                AutoCompleteTextView nameView = row.findViewById(R.id.MealIngredientRowName);
                TextInputEditText amountView = row.findViewById(R.id.MealIngredientRowAmount);
                TextInputEditText unitView = row.findViewById(R.id.MealIngredientRowUnit);
                String name = nameView.getText().toString().trim();
                String unit = unitView.getText().toString().trim();
                double amount = 0;
                try { amount = Double.parseDouble(amountView.getText().toString().trim()); } catch (NumberFormatException ignored) {}
                if (!name.isEmpty()) {
                    String ingredientId = findIngredientId(ingredients, name);
                    builder.ingredient(ingredientId, name, amount, unit);
                }
            }

            Recipe recipe = builder.build();
            recipe.totalCalories = cal;
            recipe.totalProtein = pro;
            recipe.totalCarbs = carbs;
            recipe.totalFat = fat;

            // Preserve fields from existing recipe
            if (isEdit) {
                recipe.id = existing.id;
                recipe.lastUsed = existing.lastUsed;
                recipe.usageCount = existing.usageCount;
                recipe.ratings = existing.ratings;
                recipe.shelfLifeDays = existing.shelfLifeDays;
                recipe.minServings = existing.minServings;
                recipe.maxServings = existing.maxServings;
                recipe.scalingPrecision = existing.scalingPrecision;
            }

            listener.onRecipeSaved(recipe);
            dialog.dismiss();
        }, isEdit ? dlg -> {
            dlg.setButton(AlertDialog.BUTTON_NEUTRAL,
                    ctx.getString(R.string.action_delete), (d, w) -> {});
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dlg.dismiss();
                DialogHelper.showDeleteConfirmation(ctx,
                        R.string.meal_recipe_delete_title, R.string.meal_recipe_delete_message,
                        () -> listener.onRecipeDeleted(existing.id));
            });
        } : null);
    }

    private void addIngredientRow(Context ctx, LinearLayout container,
                                  List<String> ingredientNames,
                                  @Nullable Recipe.RecipeIngredient prefill) {
        View row = LayoutInflater.from(ctx).inflate(R.layout.meal_recipe_ingredient_row, container, false);
        AutoCompleteTextView nameView = row.findViewById(R.id.MealIngredientRowName);
        TextInputEditText amountView = row.findViewById(R.id.MealIngredientRowAmount);
        TextInputEditText unitView = row.findViewById(R.id.MealIngredientRowUnit);
        ImageButton removeBtn = row.findViewById(R.id.MealIngredientRowRemove);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx,
                android.R.layout.simple_dropdown_item_1line, ingredientNames);
        nameView.setAdapter(adapter);

        if (prefill != null) {
            nameView.setText(prefill.ingredientName());
            String amountStr = prefill.amount() == (int) prefill.amount()
                    ? String.valueOf((int) prefill.amount())
                    : String.valueOf(prefill.amount());
            amountView.setText(amountStr);
            unitView.setText(prefill.unit());
        }

        removeBtn.setOnClickListener(v -> container.removeView(row));
        container.addView(row);
    }

    @Nullable
    private static String findIngredientId(List<Ingredient> ingredients, String name) {
        for (Ingredient ing : ingredients) {
            if (ing.name.equalsIgnoreCase(name)) return ing.id;
        }
        return null;
    }
}
