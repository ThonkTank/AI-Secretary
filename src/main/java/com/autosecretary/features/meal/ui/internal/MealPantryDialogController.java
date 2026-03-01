package com.autosecretary.features.meal.ui.internal;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.shared.ui.SpinnerHelper;

import java.util.Arrays;

/**
 * Manages the "add pantry item" dialog. Validates ingredient name, amount, unit, shelf life,
 * and storage location, then notifies the listener on successful submission.
 */
public class MealPantryDialogController {

    private static final int DEFAULT_SPINNER_SELECTION = 0;

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
        EditText nameField = content.findViewById(R.id.MealPantryName);
        EditText amountField = content.findViewById(R.id.MealPantryAmount);
        EditText unitField = content.findViewById(R.id.MealPantryUnit);
        EditText shelfLifeDaysField = content.findViewById(R.id.MealPantryShelfLifeDays);
        Spinner locationSpinner = content.findViewById(R.id.MealPantryLocation);
        SpinnerHelper.bindList(locationSpinner, Arrays.asList(PantryItem.StorageLocation.values()),
                sl -> sl.label, ctx);
        locationSpinner.setSelection(DEFAULT_SPINNER_SELECTION);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.meal_pantry_dialog_title)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.meal_pantry_dialog_save, null)
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
            String shelfLifeStr = MealDialogValidation.requireNonEmpty(shelfLifeDaysField,
                    ctx.getString(R.string.meal_field_shelf_life), ctx);
            if (shelfLifeStr == null) return;
            Integer shelfLifeDays = MealDialogValidation.parseInt(shelfLifeDaysField,
                    ctx.getString(R.string.meal_field_shelf_life_label), ctx);
            if (shelfLifeDays == null || shelfLifeDays < 0) return;
            int locationIndex = locationSpinner.getSelectedItemPosition();
            PantryItem.StorageLocation[] locations = PantryItem.StorageLocation.values();
            if (locationIndex < 0 || locationIndex >= locations.length) return;
            PantryItem.StorageLocation location = locations[locationIndex];
            listener.onPantryItemSubmitted(ingredientName, amount, unitStr, location, shelfLifeDays);
            dialog.dismiss();
        }));
        dialog.show();
    }
}
