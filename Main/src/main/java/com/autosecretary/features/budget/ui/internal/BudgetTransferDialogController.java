package com.autosecretary.features.budget.ui.internal;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.features.budget.domain.BudgetAccount;
import com.autosecretary.features.budget.domain.AmountParser;
import com.autosecretary.shared.ui.DialogHelper;
import com.autosecretary.shared.ui.DialogValidation;
import com.autosecretary.shared.ui.SpinnerHelper;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.util.List;

/**
 * Manages the account-to-account transfer dialog.
 * Creates a pair of linked {@code INTERNAL_TRANSFER} transactions (debit + credit) via
 * {@link Listener#onTransferSubmitted}; the actual booking is done by the caller.
 *
 * <p>Requires at least two active accounts — shows an informational dialog and returns early
 * if fewer accounts are available.
 */
public class BudgetTransferDialogController {

    public interface Listener {
        void onTransferSubmitted(String sourceAccountId,
                                 String targetAccountId,
                                 String amount,
                                 LocalDate bookingDate,
                                 String note);
    }

    private final Fragment fragment;
    private final Listener listener;

    public BudgetTransferDialogController(Fragment fragment, Listener listener) {
        this.fragment = fragment;
        this.listener = listener;
    }

    public void show(List<BudgetAccount> accounts) {
        Context ctx = fragment.requireContext();
        if (accounts == null || accounts.size() < 2) {
            new AlertDialog.Builder(ctx)
                    .setMessage(R.string.budget_transfer_requires_two_accounts)
                    .setPositiveButton(R.string.action_ok, null)
                    .show();
            return;
        }

        View dialogView = LayoutInflater.from(ctx)
                .inflate(R.layout.budget_transfer_dialog, null);
        Spinner sourceAccountSpinner = dialogView.findViewById(R.id.BudgetTransferSourceAccount);
        Spinner targetAccountSpinner = dialogView.findViewById(R.id.BudgetTransferTargetAccount);
        TextInputEditText amountInput = dialogView.findViewById(R.id.BudgetTransferAmount);
        TextInputEditText dateInput = dialogView.findViewById(R.id.BudgetTransferDate);
        TextInputEditText noteInput = dialogView.findViewById(R.id.BudgetTransferNote);

        SpinnerHelper.bindList(sourceAccountSpinner, accounts, BudgetAccount::name, ctx);
        SpinnerHelper.bindList(targetAccountSpinner, accounts, BudgetAccount::name, ctx);
        targetAccountSpinner.setSelection(1); // Default target to second account so source ≠ target.

        dateInput.setText(LocalDate.now().toString());
        DialogHelper.setupDatePicker(dateInput, ctx);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.budget_transfer_title)
                .setView(dialogView)
                .setPositiveButton(R.string.budget_transfer_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        DialogHelper.showWithValidation(dialog, () -> {
            int sourceIdx = sourceAccountSpinner.getSelectedItemPosition();
            int targetIdx = targetAccountSpinner.getSelectedItemPosition();
            if (sourceIdx < 0 || sourceIdx >= accounts.size()
                    || targetIdx < 0 || targetIdx >= accounts.size()) {
                return;
            }
            if (sourceIdx == targetIdx) {
                Toast.makeText(ctx, R.string.budget_transfer_same_account, Toast.LENGTH_SHORT).show();
                return;
            }

            LocalDate bookingDate = DialogValidation.parseDate(dateInput, ctx);
            if (bookingDate == null) return;

            String amountStr = DialogValidation.requireNonEmpty(amountInput,
                    ctx.getString(R.string.budget_dialog_amount_label), ctx);
            if (amountStr == null) return;
            if (AmountParser.parseAmountCents(amountStr) == null) {
                amountInput.setError(ctx.getString(R.string.budget_status_invalid_amount));
                amountInput.requestFocus();
                return;
            }
            String note = DialogValidation.textOfNullable(noteInput);
            listener.onTransferSubmitted(
                    accounts.get(sourceIdx).id(),
                    accounts.get(targetIdx).id(),
                    amountStr,
                    bookingDate,
                    note
            );
            dialog.dismiss();
        });
    }

}
