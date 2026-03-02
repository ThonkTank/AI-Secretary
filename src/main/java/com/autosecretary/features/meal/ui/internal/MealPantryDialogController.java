package com.autosecretary.features.meal.ui.internal;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.shared.ui.DialogHelper;
import com.autosecretary.shared.ui.DialogValidation;
import com.autosecretary.shared.ui.SpinnerHelper;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Arrays;

/**
 * Manages the "add pantry item" dialog. Validates ingredient name, amount, unit, shelf life,
 * and storage location, then notifies the listener on successful submission.
 */
public class MealPantryDialogController {

    public interface Listener {
        void onPantryItemSubmitted(String name, double amount, String unit,
                                   PantryItem.StorageLocation location, int shelfLifeDays);
    }

    private final Fragment fragment;
    private final Listener listener;

    public MealPantryDialogController(Fragment fragment, Listener listener) {
        this.fragment = fragment;
        this.listener = listener;
    }

    public void show() {
        Context ctx = fragment.requireContext();
        View content = LayoutInflater.from(ctx).inflate(R.layout.meal_pantry_create_dialog, null);
        TextInputEditText nameField = content.findViewById(R.id.MealPantryName);
        TextInputEditText amountField = content.findViewById(R.id.MealPantryAmount);
        TextInputEditText unitField = content.findViewById(R.id.MealPantryUnit);
        TextInputEditText shelfLifeDaysField = content.findViewById(R.id.MealPantryShelfLifeDays);
        Spinner locationSpinner = content.findViewById(R.id.MealPantryLocation);
        SpinnerHelper.bindList(locationSpinner, Arrays.asList(PantryItem.StorageLocation.values()),
                sl -> sl.label, ctx);
        locationSpinner.setSelection(0);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.meal_pantry_dialog_title)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.meal_pantry_dialog_save, null)
                .create();
        DialogHelper.showWithValidation(dialog, () -> {
            String ingredientName = DialogValidation.requireNonEmpty(nameField,
                    ctx.getString(R.string.meal_field_ingredient_name), ctx);
            if (ingredientName == null) return;
            Double amount = DialogValidation.parseDouble(amountField,
                    ctx.getString(R.string.meal_field_amount_label), ctx);
            if (amount == null) return;
            if (amount < 0) {
                amountField.setError(ctx.getString(R.string.meal_error_negative_number,
                        ctx.getString(R.string.meal_field_amount_label)));
                amountField.requestFocus();
                return;
            }
            String unitStr = DialogValidation.requireNonEmpty(unitField,
                    ctx.getString(R.string.meal_field_unit), ctx);
            if (unitStr == null) return;
            Integer shelfLifeDays = DialogValidation.parseInt(shelfLifeDaysField,
                    ctx.getString(R.string.meal_field_shelf_life_label), ctx);
            if (shelfLifeDays == null) return;
            if (shelfLifeDays < 0) {
                shelfLifeDaysField.setError(ctx.getString(R.string.meal_error_negative_number,
                        ctx.getString(R.string.meal_field_shelf_life_label)));
                shelfLifeDaysField.requestFocus();
                return;
            }
            PantryItem.StorageLocation location = SpinnerHelper.enumAtPosition(
                    locationSpinner, PantryItem.StorageLocation.values());
            if (location == null) return;
            listener.onPantryItemSubmitted(ingredientName, amount, unitStr, location, shelfLifeDays);
            dialog.dismiss();
        });
    }
}
