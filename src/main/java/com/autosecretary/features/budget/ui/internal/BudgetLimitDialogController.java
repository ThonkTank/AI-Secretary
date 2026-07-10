package com.autosecretary.features.budget.ui.internal;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Spinner;

import com.google.android.material.textfield.TextInputLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.features.budget.application.overview.BudgetOverviewMapper;
import com.autosecretary.features.budget.domain.BudgetCategory;
import com.autosecretary.shared.ui.DialogHelper;
import com.autosecretary.shared.ui.DialogValidation;
import com.autosecretary.shared.ui.SpinnerHelper;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

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
        Context ctx = fragment.requireContext();
        List<BudgetCategory> expenseCategories = BudgetOverviewMapper
                .categoriesForDirection(allCategories, TransactionDirection.EXPENSE);

        View dialogView = LayoutInflater.from(ctx)
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
                c -> BudgetOverviewMapper.categoryLabel(c.icon(), c.name()), ctx);
        SpinnerHelper.setSelection(categorySpinner, expenseCategories,
                preSelectedCategoryId, BudgetCategory::id);

        // Pre-populate the amount field when editing an existing limit (baseLimitCents > 0).
        // If zero or negative (new limit), the field is intentionally left blank.
        if (baseLimitCents > 0) {
            amountInput.setText(CurrencyFormatter.centsToDecimal(baseLimitCents));
        }

        // Show the carryover input only when rollover is enabled.
        rolloverSwitch.setOnCheckedChangeListener((v, checked) ->
                rolloverCarryoverLayout.setVisibility(checked ? View.VISIBLE : View.GONE));

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.budget_edit_limit_title)
                .setView(dialogView)
                .setPositiveButton(R.string.budget_limit_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        DialogHelper.showWithValidation(dialog, () -> {
            String amountStr = DialogValidation.requireNonEmpty(amountInput,
                    ctx.getString(R.string.budget_dialog_amount_label), ctx);
            if (amountStr == null) return;

            String categoryId = SpinnerHelper.idAtPosition(expenseCategories,
                    categorySpinner.getSelectedItemPosition(), BudgetCategory::id);
            if (categoryId == null) return;

            listener.onSaveBudgetLimit(categoryId, amountStr,
                    rolloverSwitch.isChecked(), DialogValidation.textOf(rolloverCarryoverInput));
            dialog.dismiss();
        });
    }
}
