package com.autosecretary.features.budget.ui.internal;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Spinner;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.features.budget.data.entity.BudgetCategory;
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
                     List<BudgetCategory> allCategories) {
        List<BudgetCategory> expenseCategories = allCategories.stream()
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

        new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.budget_edit_limit_title)
                .setView(dialogView)
                .setPositiveButton(R.string.budget_limit_save, (dialog, which) -> {
                    String amountStr = SpinnerHelper.textOf(amountInput);
                    if (amountStr.isEmpty()) return;

                    String categoryId = SpinnerHelper.idAtPosition(expenseCategories,
                            categorySpinner.getSelectedItemPosition(), c -> c.id);
                    if (categoryId == null) return;

                    listener.onSaveBudgetLimit(categoryId, amountStr,
                            rolloverSwitch.isChecked(), SpinnerHelper.textOf(rolloverCarryoverInput));
                })
                .setNegativeButton(R.string.budget_dialog_cancel, null)
                .show();
    }
}
