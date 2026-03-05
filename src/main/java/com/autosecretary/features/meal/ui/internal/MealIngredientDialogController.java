package com.autosecretary.features.meal.ui.internal;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Spinner;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.shared.ui.DialogHelper;
import com.autosecretary.shared.ui.DialogValidation;
import com.autosecretary.shared.ui.SpinnerHelper;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Arrays;

/**
 * Manages the ingredient create/edit dialog. Validates name, food group, unit,
 * and grams-per-unit as required. Nutrition and flags are optional.
 */
public class MealIngredientDialogController {

    public interface Listener {
        void onIngredientSaved(Ingredient ingredient);
        void onIngredientDeleted(String ingredientId);
    }

    private final Fragment fragment;
    private final Listener listener;

    public MealIngredientDialogController(Fragment fragment, Listener listener) {
        this.fragment = fragment;
        this.listener = listener;
    }

    public void show() {
        showInternal(null);
    }

    public void showEdit(Ingredient existing) {
        showInternal(existing);
    }

    private void showInternal(@Nullable Ingredient existing) {
        Context ctx = fragment.requireContext();
        boolean isEdit = existing != null;

        View content = LayoutInflater.from(ctx).inflate(R.layout.meal_ingredient_edit_dialog, null);
        TextInputEditText nameField = content.findViewById(R.id.MealIngredientName);
        Spinner foodGroupSpinner = content.findViewById(R.id.MealIngredientFoodGroup);
        TextInputEditText unitField = content.findViewById(R.id.MealIngredientUnit);
        TextInputEditText gramsField = content.findViewById(R.id.MealIngredientGramsPerUnit);
        TextInputEditText caloriesField = content.findViewById(R.id.MealIngredientCalories);
        TextInputEditText proteinField = content.findViewById(R.id.MealIngredientProtein);
        TextInputEditText carbsField = content.findViewById(R.id.MealIngredientCarbs);
        TextInputEditText fatField = content.findViewById(R.id.MealIngredientFat);
        TextInputEditText shelfLifeField = content.findViewById(R.id.MealIngredientShelfLife);
        CheckBox refrigerationBox = content.findViewById(R.id.MealIngredientRefrigeration);
        CheckBox wholeUnitBox = content.findViewById(R.id.MealIngredientWholeUnit);

        // Populate food group spinner using label + icon
        SpinnerHelper.bindList(foodGroupSpinner, Arrays.asList(Ingredient.FoodGroup.values()),
                fg -> fg.icon + " " + fg.label, ctx);
        foodGroupSpinner.setSelection(0);

        // Default values
        unitField.setText("g");
        gramsField.setText("1");

        if (isEdit) {
            nameField.setText(existing.name);
            if (existing.foodGroup != null) foodGroupSpinner.setSelection(existing.foodGroup.ordinal());
            unitField.setText(existing.defaultUnit);
            gramsField.setText(String.valueOf(existing.gramsPerUnit));
            if (existing.caloriesPer100 > 0) caloriesField.setText(String.valueOf(existing.caloriesPer100));
            if (existing.proteinPer100 > 0) proteinField.setText(String.valueOf(existing.proteinPer100));
            if (existing.carbsPer100 > 0) carbsField.setText(String.valueOf(existing.carbsPer100));
            if (existing.fatPer100 > 0) fatField.setText(String.valueOf(existing.fatPer100));
            if (existing.shelfLifeDays > 0) shelfLifeField.setText(String.valueOf(existing.shelfLifeDays));
            refrigerationBox.setChecked(existing.requiresRefrigeration);
            wholeUnitBox.setChecked(existing.isWholeUnit);
        }

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(isEdit ? R.string.meal_ingredient_dialog_title_edit : R.string.meal_ingredient_dialog_title_new)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null)
                .create();

        DialogHelper.showWithValidation(dialog, () -> {
            String name = DialogValidation.requireNonEmpty(nameField,
                    ctx.getString(R.string.meal_field_ingredient_name), ctx);
            if (name == null) return;
            String unit = DialogValidation.requireNonEmpty(unitField,
                    ctx.getString(R.string.meal_field_unit), ctx);
            if (unit == null) return;
            Integer gramsPerUnit = DialogValidation.parseInt(gramsField,
                    ctx.getString(R.string.meal_field_grams_per_unit), ctx);
            if (gramsPerUnit == null || gramsPerUnit <= 0) return;

            Ingredient.FoodGroup foodGroup = SpinnerHelper.enumAtPosition(
                    foodGroupSpinner, Ingredient.FoodGroup.values());
            if (foodGroup == null) return;

            Ingredient.Builder builder = new Ingredient.Builder(name, foodGroup)
                    .unit(unit, gramsPerUnit)
                    .calories(DialogValidation.parseIntOrDefault(DialogValidation.textOf(caloriesField), 0))
                    .protein(DialogValidation.parseIntOrDefault(DialogValidation.textOf(proteinField), 0))
                    .carbs(DialogValidation.parseIntOrDefault(DialogValidation.textOf(carbsField), 0))
                    .fat(DialogValidation.parseIntOrDefault(DialogValidation.textOf(fatField), 0))
                    .shelfLife(DialogValidation.parseIntOrDefault(DialogValidation.textOf(shelfLifeField), 0));

            if (refrigerationBox.isChecked()) builder.refrigerated();
            if (wholeUnitBox.isChecked()) builder.wholeUnit();

            Ingredient ingredient = builder.build();

            // Preserve existing ID and store packages
            if (isEdit) {
                ingredient.id = existing.id;
                ingredient.storePackages = existing.storePackages;
                ingredient.isPerishable = existing.isPerishable;
            }

            listener.onIngredientSaved(ingredient);
            dialog.dismiss();
        }, isEdit ? dlg -> {
            dlg.setButton(AlertDialog.BUTTON_NEUTRAL,
                    ctx.getString(R.string.action_delete), (d, w) -> {});
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dlg.dismiss();
                DialogHelper.showDeleteConfirmation(ctx,
                        R.string.meal_ingredient_delete_title, R.string.meal_ingredient_delete_message,
                        () -> listener.onIngredientDeleted(existing.id));
            });
        } : null);
    }
}
