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

/**
 * Manages the "add shopping need" dialog. Validates ingredient name, amount, and unit,
 * then notifies the listener on successful submission.
 */
public class MealNeedDialogController {

    public interface Listener {
        void onNeedSubmitted(String name, double amount, String unit);
    }

    private final Fragment fragment;
    private final Listener listener;

    public MealNeedDialogController(Fragment fragment, Listener listener) {
        this.fragment = fragment;
        this.listener = listener;
    }

    public void show() {
        Context ctx = fragment.requireContext();
        View content = LayoutInflater.from(ctx).inflate(R.layout.meal_need_create_dialog, null);
        TextInputEditText nameField = content.findViewById(R.id.MealNeedName);
        TextInputEditText amountField = content.findViewById(R.id.MealNeedAmount);
        TextInputEditText unitField = content.findViewById(R.id.MealNeedUnit);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.meal_need_dialog_title)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.meal_need_dialog_save, null)
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
            listener.onNeedSubmitted(ingredientName, amount, unitStr);
            dialog.dismiss();
        });
    }
}
