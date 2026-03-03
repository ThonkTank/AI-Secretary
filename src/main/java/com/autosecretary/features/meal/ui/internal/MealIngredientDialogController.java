package com.autosecretary.features.meal.ui.internal;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.shared.ui.DialogHelper;
import com.autosecretary.shared.ui.DialogValidation;
import com.autosecretary.shared.ui.SpinnerHelper;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Arrays;

public class MealIngredientDialogController {

    public interface Listener {
        void onIngredientSubmitted(String name, Ingredient.FoodGroup foodGroup, String unit);
    }

    private final Fragment fragment;
    private final Listener listener;

    public MealIngredientDialogController(Fragment fragment, Listener listener) {
        this.fragment = fragment;
        this.listener = listener;
    }

    public void show() {
        Context ctx = fragment.requireContext();
        View content = LayoutInflater.from(ctx).inflate(R.layout.meal_ingredient_create_dialog, null);
        TextInputEditText nameField = content.findViewById(R.id.MealIngredientName);
        TextInputEditText unitField = content.findViewById(R.id.MealIngredientUnit);
        Spinner groupSpinner = content.findViewById(R.id.MealIngredientGroup);
        SpinnerHelper.bindList(groupSpinner, Arrays.asList(Ingredient.FoodGroup.values()), g -> g.label, ctx);
        unitField.setText("g");

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.meal_ingredient_dialog_title)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.meal_ingredient_dialog_save, null)
                .create();
        DialogHelper.showWithValidation(dialog, () -> {
            String name = DialogValidation.requireNonEmpty(nameField,
                    ctx.getString(R.string.meal_field_ingredient_name), ctx);
            if (name == null) return;
            String unit = DialogValidation.requireNonEmpty(unitField,
                    ctx.getString(R.string.meal_field_unit), ctx);
            if (unit == null) return;
            Ingredient.FoodGroup group = SpinnerHelper.enumAtPosition(groupSpinner, Ingredient.FoodGroup.values());
            if (group == null) return;
            listener.onIngredientSubmitted(name, group, unit);
            dialog.dismiss();
        });
    }
}
