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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manages the "set/edit budget limit" dialog.
 * Mirrors the controller pattern of {@link BudgetTransferDialogController}.
 */
public class BudgetLimitDialogController {

    public interface Listener {
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
        List<BudgetCategory> expenseCategories = expenseCategories(allCategories);

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

        if (baseLimitCents > 0) {
            amountInput.setText(String.format(Locale.GERMAN, "%.2f", baseLimitCents / 100.0));
        }

        new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.budget_edit_limit_title)
                .setView(dialogView)
                .setPositiveButton(R.string.budget_limit_save, (dialog, which) -> {
                    String amountStr = amountInput.getText() != null
                            ? amountInput.getText().toString().trim() : "";
                    if (amountStr.isEmpty()) return;

                    String categoryId = SpinnerHelper.idAtPosition(expenseCategories,
                            categorySpinner.getSelectedItemPosition(), c -> c.id);
                    if (categoryId == null) return;

                    String rolloverCarryoverStr = rolloverCarryoverInput.getText() != null
                            ? rolloverCarryoverInput.getText().toString().trim() : "";

                    listener.onSaveBudgetLimit(categoryId, amountStr,
                            rolloverSwitch.isChecked(), rolloverCarryoverStr);
                })
                .setNegativeButton(R.string.budget_dialog_cancel, null)
                .show();
    }

    private static List<BudgetCategory> expenseCategories(List<BudgetCategory> allCategories) {
        List<BudgetCategory> result = new ArrayList<>();
        for (BudgetCategory category : allCategories) {
            if (category.direction == TransactionDirection.EXPENSE) {
                result.add(category);
            }
        }
        return result;
    }
}
