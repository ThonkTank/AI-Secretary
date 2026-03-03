package com.autosecretary.features.meal.ui.internal;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.shared.ui.DialogHelper;
import com.autosecretary.shared.ui.DialogValidation;
import com.google.android.material.textfield.TextInputEditText;

public class MealRecipeDialogController {

    public interface Listener {
        void onRecipeSubmitted(String title, String description, String instructions, int servings);
    }

    private final Fragment fragment;
    private final Listener listener;

    public MealRecipeDialogController(Fragment fragment, Listener listener) {
        this.fragment = fragment;
        this.listener = listener;
    }

    public void show() {
        Context ctx = fragment.requireContext();
        View content = LayoutInflater.from(ctx).inflate(R.layout.meal_recipe_create_dialog, null);
        TextInputEditText titleField = content.findViewById(R.id.MealRecipeTitle);
        TextInputEditText descriptionField = content.findViewById(R.id.MealRecipeDescription);
        TextInputEditText instructionsField = content.findViewById(R.id.MealRecipeInstructions);
        TextInputEditText servingsField = content.findViewById(R.id.MealRecipeServings);
        servingsField.setText("2");

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.meal_recipe_dialog_title)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.meal_recipe_dialog_save, null)
                .create();
        DialogHelper.showWithValidation(dialog, () -> {
            String title = DialogValidation.requireNonEmpty(titleField,
                    ctx.getString(R.string.meal_field_recipe_title), ctx);
            if (title == null) return;
            Integer servings = DialogValidation.parseInt(servingsField,
                    ctx.getString(R.string.meal_field_servings), ctx);
            if (servings == null || servings <= 0) return;
            String description = descriptionField.getText() == null ? "" : descriptionField.getText().toString().trim();
            String instructions = instructionsField.getText() == null ? "" : instructionsField.getText().toString().trim();
            listener.onRecipeSubmitted(title, description, instructions, servings);
            dialog.dismiss();
        });
    }
}
