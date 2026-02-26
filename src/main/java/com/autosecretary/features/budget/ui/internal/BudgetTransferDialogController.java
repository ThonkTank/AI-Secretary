package com.autosecretary.features.budget.ui.internal;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

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
        if (accounts == null || accounts.size() < 2) {
            new AlertDialog.Builder(fragment.requireContext())
                    .setMessage(R.string.budget_transfer_requires_two_accounts)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        View dialogView = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.budget_transfer_dialog, null);
        Spinner sourceAccountSpinner = dialogView.findViewById(R.id.BudgetTransferSourceAccount);
        Spinner targetAccountSpinner = dialogView.findViewById(R.id.BudgetTransferTargetAccount);
        TextInputEditText amountInput = dialogView.findViewById(R.id.BudgetTransferAmount);
        TextInputEditText dateInput = dialogView.findViewById(R.id.BudgetTransferDate);
        TextInputEditText noteInput = dialogView.findViewById(R.id.BudgetTransferNote);

        List<String> accountNames = new ArrayList<>();
        for (BudgetAccount account : accounts) {
            accountNames.add(account.name);
        }
        SpinnerHelper.bindNames(sourceAccountSpinner, accountNames, fragment.requireContext());
        SpinnerHelper.bindNames(targetAccountSpinner, accountNames, fragment.requireContext());
        targetAccountSpinner.setSelection(1);

        dateInput.setText(LocalDate.now().toString());

        new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.budget_transfer_title)
                .setView(dialogView)
                .setPositiveButton(R.string.budget_transfer_save, (dialog, which) -> {
                    int sourceIdx = sourceAccountSpinner.getSelectedItemPosition();
                    int targetIdx = targetAccountSpinner.getSelectedItemPosition();
                    if (sourceIdx < 0 || sourceIdx >= accounts.size()
                            || targetIdx < 0 || targetIdx >= accounts.size()) {
                        return;
                    }

                    String amountStr = amountInput.getText() != null
                            ? amountInput.getText().toString().trim() : "";
                    String note = noteInput.getText() != null
                            ? noteInput.getText().toString().trim() : "";
                    String dateStr = dateInput.getText() != null
                            ? dateInput.getText().toString().trim() : "";

                    LocalDate bookingDate;
                    try {
                        bookingDate = LocalDate.parse(dateStr);
                    } catch (DateTimeParseException ex) {
                        bookingDate = LocalDate.now();
                    }

                    listener.onTransferSubmitted(
                            accounts.get(sourceIdx).id,
                            accounts.get(targetIdx).id,
                            amountStr,
                            bookingDate,
                            note.isEmpty() ? null : note
                    );
                })
                .setNegativeButton(R.string.budget_dialog_cancel, null)
                .show();
    }
}
