package com.autosecretary.features.budget.ui.internal;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Spinner;

import com.google.android.material.textfield.TextInputLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.features.budget.data.entity.BudgetCategoryEntity;
import com.autosecretary.shared.ui.SpinnerHelper;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Manages the "set/edit budget limit" dialog.
 * Mirrors the controller pattern of {@link BudgetTransferDialogController}.
 */
public class BudgetLimitDialogController {

    public interface Listener {
        /**
         * Called when the user confirms the limit dialog.
         *
         * @param categoryId          UUID of the expense category.
         * @param amountStr           Locale-formatted decimal string, e.g. {@code "150,00"} (German).
         *                            Parsing is the caller's responsibility.
         * @param rolloverEnabled     Whether unspent budget carries over into the next month.
         * @param rolloverCarryoverStr Optional Euro amount string for an initial carryover balance;
         *                            empty string means no pre-seeded carryover.
         */
        void onSaveBudgetLimit(String categoryId, String amountStr,
                               boolean rolloverEnabled, String rolloverCarryoverStr);
    }

    private final Fragment fragment;
    private final Listener listener;

    public BudgetLimitDialogController(Fragment fragment, Listener listener) {
        this.fragment = fragment;
        this.listener = listener;
    }

    public void show(@Nullable String preSelectedCategoryId, long baseLimitCents,
                     List<BudgetCategoryEntity> allCategories) {
        List<BudgetCategoryEntity> expenseCategories = allCategories.stream()
                .filter(c -> c.direction == TransactionDirection.EXPENSE)
                .collect(Collectors.toList());

        View dialogView = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.budget_edit_limit_dialog, null);
        Spinner categorySpinner = dialogView.findViewById(R.id.BudgetLimitDialogCategory);
        TextInputEditText amountInput = dialogView.findViewById(R.id.BudgetLimitDialogAmount);
        SwitchMaterial rolloverSwitch =
                dialogView.findViewById(R.id.BudgetLimitDialogRolloverEnabled);
        TextInputEditText rolloverCarryoverInput =
                dialogView.findViewById(R.id.BudgetLimitDialogRolloverCarryover);
        TextInputLayout rolloverCarryoverLayout =
                dialogView.findViewById(R.id.BudgetLimitDialogRolloverCarryoverLayout);

        SpinnerHelper.bindList(categorySpinner, expenseCategories,
                c -> BudgetSummaryPresentationMapper.categoryLabel(c.icon, c.name),
                fragment.requireContext());
        SpinnerHelper.setSelection(categorySpinner, expenseCategories,
                preSelectedCategoryId, c -> c.id);

        // Pre-populate the amount field when editing an existing limit (baseLimitCents > 0).
        // If zero or negative (new limit), the field is intentionally left blank.
        if (baseLimitCents > 0) {
            amountInput.setText(String.format(Locale.GERMAN, "%.2f", baseLimitCents / 100.0));
        }

        // Show the carryover input only when rollover is enabled.
        rolloverSwitch.setOnCheckedChangeListener((v, checked) ->
                rolloverCarryoverLayout.setVisibility(checked ? View.VISIBLE : View.GONE));

        AlertDialog dialog = new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.budget_edit_limit_title)
                .setView(dialogView)
                .setPositiveButton(R.string.budget_limit_save, null)
                .setNegativeButton(R.string.budget_dialog_cancel, null)
                .create();

        // setOnShowListener + null in setPositiveButton: standard pattern to prevent the
        // AlertDialog from auto-dismissing before validation has a chance to show an error.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String amountStr = SpinnerHelper.textOf(amountInput);
            if (amountStr.isEmpty()) {
                amountInput.setError(fragment.getString(R.string.budget_status_invalid_amount));
                return;
            }

            String categoryId = SpinnerHelper.idAtPosition(expenseCategories,
                    categorySpinner.getSelectedItemPosition(), c -> c.id);
            if (categoryId == null) return;

            listener.onSaveBudgetLimit(categoryId, amountStr,
                    rolloverSwitch.isChecked(), SpinnerHelper.textOf(rolloverCarryoverInput));
            dialog.dismiss();
        }));

        dialog.show();
    }
}
