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
import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.shared.ui.DialogHelper;
import com.autosecretary.shared.ui.DialogValidation;
import com.autosecretary.shared.ui.SpinnerHelper;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Arrays;

/**
 * Manages the household member create/edit dialog. Validates name, birth year, weight, height,
 * then notifies the listener.
 */
public class MealHouseholdDialogController {

    private static final String[] GENDER_LABELS = new String[3];

    public interface Listener {
        void onMemberSaved(HouseholdMember member);
        void onMemberDeleted(String memberId);
    }

    private final Fragment fragment;
    private final Listener listener;

    public MealHouseholdDialogController(Fragment fragment, Listener listener) {
        this.fragment = fragment;
        this.listener = listener;
    }

    /** Opens a create dialog for a new household member. */
    public void show() {
        showInternal(null);
    }

    /** Opens an edit dialog for an existing household member. */
    public void showEdit(HouseholdMember existing) {
        showInternal(existing);
    }

    private void showInternal(@Nullable HouseholdMember existing) {
        Context ctx = fragment.requireContext();
        boolean isEdit = existing != null;
        if (GENDER_LABELS[0] == null) {
            GENDER_LABELS[0] = ctx.getString(R.string.meal_household_gender_male);
            GENDER_LABELS[1] = ctx.getString(R.string.meal_household_gender_female);
            GENDER_LABELS[2] = ctx.getString(R.string.meal_household_gender_other);
        }

        View content = LayoutInflater.from(ctx).inflate(R.layout.meal_household_edit_dialog, null);
        TextInputEditText nameField = content.findViewById(R.id.MealHouseholdName);
        TextInputEditText birthYearField = content.findViewById(R.id.MealHouseholdBirthYear);
        Spinner genderSpinner = content.findViewById(R.id.MealHouseholdGender);
        TextInputEditText weightField = content.findViewById(R.id.MealHouseholdWeight);
        TextInputEditText heightField = content.findViewById(R.id.MealHouseholdHeight);
        Spinner activitySpinner = content.findViewById(R.id.MealHouseholdActivity);
        CheckBox activeBox = content.findViewById(R.id.MealHouseholdActive);

        SpinnerHelper.bindList(genderSpinner, Arrays.asList(GENDER_LABELS), s -> s, ctx);
        SpinnerHelper.bindList(activitySpinner, Arrays.asList(HouseholdMember.ActivityLevel.values()),
                al -> al.label, ctx);

        if (isEdit) {
            nameField.setText(existing.name);
            if (existing.birthYear > 0) birthYearField.setText(String.valueOf(existing.birthYear));
            if (existing.gender != null) genderSpinner.setSelection(existing.gender.ordinal());
            if (existing.weightKg > 0) weightField.setText(String.valueOf(existing.weightKg));
            if (existing.heightCm > 0) heightField.setText(String.valueOf(existing.heightCm));
            if (existing.activityLevel != null) activitySpinner.setSelection(existing.activityLevel.ordinal());
            activeBox.setChecked(existing.isActive);
        } else {
            genderSpinner.setSelection(0);
            activitySpinner.setSelection(2); // MODERATE
        }

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(isEdit ? R.string.meal_household_dialog_title_edit : R.string.meal_household_dialog_title_new)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null)
                .create();

        DialogHelper.showWithValidation(dialog, () -> {
            String name = DialogValidation.requireNonEmpty(nameField,
                    ctx.getString(R.string.meal_household_field_name), ctx);
            if (name == null) return;
            Integer birthYear = DialogValidation.parseInt(birthYearField,
                    ctx.getString(R.string.meal_household_field_birth_year), ctx);
            if (birthYear == null) return;
            Integer weight = DialogValidation.parseInt(weightField,
                    ctx.getString(R.string.meal_household_field_weight), ctx);
            if (weight == null || weight <= 0) return;
            Integer height = DialogValidation.parseInt(heightField,
                    ctx.getString(R.string.meal_household_field_height), ctx);
            if (height == null || height <= 0) return;

            int genderIdx = genderSpinner.getSelectedItemPosition();
            HouseholdMember.Gender gender = HouseholdMember.Gender.values()[
                    Math.min(genderIdx, HouseholdMember.Gender.values().length - 1)];

            HouseholdMember.ActivityLevel activity = SpinnerHelper.enumAtPosition(
                    activitySpinner, HouseholdMember.ActivityLevel.values());
            if (activity == null) activity = HouseholdMember.ActivityLevel.MODERATE;

            HouseholdMember member = new HouseholdMember.Builder(name, gender)
                    .birthYear(birthYear)
                    .weightKg(weight)
                    .heightCm(height)
                    .activityLevel(activity)
                    .active(activeBox.isChecked())
                    .build();

            if (isEdit) {
                member.id = existing.id;
                member.targetWeightKg = existing.targetWeightKg;
            }

            listener.onMemberSaved(member);
            dialog.dismiss();
        }, isEdit ? dlg -> {
            dlg.setButton(AlertDialog.BUTTON_NEUTRAL,
                    ctx.getString(R.string.action_delete), (d, w) -> {});
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dlg.dismiss();
                DialogHelper.showDeleteConfirmation(ctx,
                        R.string.meal_household_delete_title, R.string.meal_household_delete_message,
                        () -> listener.onMemberDeleted(existing.id));
            });
        } : null);
    }
}
