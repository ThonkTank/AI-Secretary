package com.autosecretary.features.meal.ui.internal;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.shared.ui.DialogHelper;
import com.autosecretary.shared.ui.DialogValidation;
import com.autosecretary.shared.ui.SpinnerHelper;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.util.Arrays;

public class MealHouseholdDialogController {

    public interface Listener {
        void onHouseholdSubmitted(String name, HouseholdMember.Gender gender, int birthYear);
    }

    private final Fragment fragment;
    private final Listener listener;

    public MealHouseholdDialogController(Fragment fragment, Listener listener) {
        this.fragment = fragment;
        this.listener = listener;
    }

    public void show() {
        Context ctx = fragment.requireContext();
        View content = LayoutInflater.from(ctx).inflate(R.layout.meal_household_create_dialog, null);
        TextInputEditText nameField = content.findViewById(R.id.MealHouseholdName);
        TextInputEditText birthYearField = content.findViewById(R.id.MealHouseholdBirthYear);
        Spinner genderSpinner = content.findViewById(R.id.MealHouseholdGender);
        SpinnerHelper.bindList(genderSpinner, Arrays.asList(HouseholdMember.Gender.values()), Enum::name, ctx);
        birthYearField.setText(String.valueOf(LocalDate.now().minusYears(30).getYear()));

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.meal_household_dialog_title)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.meal_household_dialog_save, null)
                .create();
        DialogHelper.showWithValidation(dialog, () -> {
            String name = DialogValidation.requireNonEmpty(nameField,
                    ctx.getString(R.string.meal_field_name), ctx);
            if (name == null) return;
            Integer birthYear = DialogValidation.parseInt(birthYearField,
                    ctx.getString(R.string.meal_field_birth_year), ctx);
            if (birthYear == null || birthYear < 1900 || birthYear > LocalDate.now().getYear()) return;
            HouseholdMember.Gender gender = SpinnerHelper.enumAtPosition(genderSpinner, HouseholdMember.Gender.values());
            if (gender == null) return;
            listener.onHouseholdSubmitted(name, gender, birthYear);
            dialog.dismiss();
        });
    }
}
