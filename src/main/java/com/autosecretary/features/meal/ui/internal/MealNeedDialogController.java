package com.autosecretary.features.meal.ui.internal;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;

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
        EditText nameField = content.findViewById(R.id.MealNeedName);
        EditText amountField = content.findViewById(R.id.MealNeedAmount);
        EditText unitField = content.findViewById(R.id.MealNeedUnit);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.meal_need_dialog_title)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.meal_need_dialog_save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String ingredientName = MealDialogValidation.requireNonEmpty(nameField,
                    ctx.getString(R.string.meal_field_ingredient_name), ctx);
            if (ingredientName == null) return;
            String amountStr = MealDialogValidation.requireNonEmpty(amountField,
                    ctx.getString(R.string.meal_field_amount), ctx);
            if (amountStr == null) return;
            Double amount = MealDialogValidation.parseDouble(amountField,
                    ctx.getString(R.string.meal_field_amount_label), ctx);
            if (amount == null || amount < 0) return;
            String unitStr = MealDialogValidation.requireNonEmpty(unitField,
                    ctx.getString(R.string.meal_field_unit), ctx);
            if (unitStr == null) return;
            listener.onNeedSubmitted(ingredientName, amount, unitStr);
            dialog.dismiss();
        }));
        dialog.show();
    }
}
