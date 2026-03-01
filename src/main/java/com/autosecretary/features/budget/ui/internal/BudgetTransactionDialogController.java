package com.autosecretary.features.budget.ui.internal;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;

import com.google.android.material.button.MaterialButtonToggleGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.features.budget.data.entity.BudgetAccountEntity;
import com.autosecretary.shared.ui.SpinnerHelper;
import com.autosecretary.features.budget.data.entity.BudgetCategoryEntity;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.ui.state.BudgetTransactionRow;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Manages add, edit, and delete dialogs for budget transactions.
 * Mirrors the controller pattern of {@link BudgetTransferDialogController} and
 * {@link BudgetRecurringSuggestionsDialogController}.
 */
public class BudgetTransactionDialogController {

    public interface Listener {
        void onAddTransaction(String amountStr, boolean isExpense, String categoryId,
                              String note, LocalDate date, String accountId);

        void onUpdateTransaction(String transactionId, String amountStr, boolean isExpense,
                                 String categoryId, String note, LocalDate date, String accountId);

        void onDeleteTransaction(String transactionId);
    }

    private final Fragment fragment;
    private final Listener listener;

    public BudgetTransactionDialogController(Fragment fragment, Listener listener) {
        this.fragment = fragment;
        this.listener = listener;
    }

    public void showAdd(List<BudgetCategoryEntity> allCategories, List<BudgetAccountEntity> allAccounts) {
        show(null, allCategories, allAccounts);
    }

    public void showEdit(BudgetTransactionRow existingRow,
                         List<BudgetCategoryEntity> allCategories,
                         List<BudgetAccountEntity> allAccounts) {
        show(existingRow, allCategories, allAccounts);
    }

    public void showDeleteConfirmation(BudgetTransactionRow row) {
        new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.budget_delete_title)
                .setMessage(R.string.budget_delete_message)
                .setPositiveButton(R.string.budget_delete_confirm, (dialog, which) ->
                        listener.onDeleteTransaction(row.getTransactionId()))
                .setNegativeButton(R.string.budget_dialog_cancel, null)
                .show();
    }

    private void show(@Nullable BudgetTransactionRow existingRow,
                      List<BudgetCategoryEntity> allCategories,
                      List<BudgetAccountEntity> allAccounts) {
        View dialogView = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.budget_add_transaction_dialog, null);
        TextInputEditText amountInput = dialogView.findViewById(R.id.BudgetDialogAmount);
        MaterialButtonToggleGroup typeGroup = dialogView.findViewById(R.id.BudgetDialogTypeGroup);
        Spinner categorySpinner = dialogView.findViewById(R.id.BudgetDialogCategory);
        TextInputEditText noteInput = dialogView.findViewById(R.id.BudgetDialogNote);
        TextInputEditText dateInput = dialogView.findViewById(R.id.BudgetDialogDate);
        Spinner accountSpinner = dialogView.findViewById(R.id.BudgetDialogAccount);

        boolean isExpense = existingRow == null || existingRow.isExpense();
        SpinnerHelper.bindList(categorySpinner, categoriesForType(allCategories, isExpense),
                c -> BudgetSummaryPresentationMapper.categoryLabel(c.icon, c.name),
                fragment.requireContext());
        SpinnerHelper.bindList(accountSpinner, activeAccounts(allAccounts),
                a -> a.name, fragment.requireContext());

        typeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            SpinnerHelper.bindList(categorySpinner,
                    categoriesForType(allCategories, checkedId == R.id.BudgetDialogTypeExpense),
                    c -> BudgetSummaryPresentationMapper.categoryLabel(c.icon, c.name),
                    fragment.requireContext());
        });

        LocalDate selectedDate = existingRow != null && existingRow.getBookingDate() != null
                ? existingRow.getBookingDate() : LocalDate.now();

        if (existingRow != null) {
            amountInput.setText(String.format(Locale.GERMAN, "%.2f",
                    Math.abs(existingRow.getAmountCents()) / 100.0));
            String existingNote = existingRow.getNote();
            noteInput.setText(existingNote != null ? existingNote : "");
            typeGroup.check(existingRow.isExpense()
                    ? R.id.BudgetDialogTypeExpense : R.id.BudgetDialogTypeIncome);
            SpinnerHelper.setSelection(categorySpinner,
                    categoriesForType(allCategories, existingRow.isExpense()),
                    existingRow.getCategoryId(), c -> c.id);
            SpinnerHelper.setSelection(accountSpinner, activeAccounts(allAccounts),
                    existingRow.getAccountId(), a -> a.id);
        }

        dateInput.setText(selectedDate.toString());

        int titleRes = existingRow == null
                ? R.string.budget_dialog_title
                : R.string.budget_dialog_edit_title;
        int positiveRes = existingRow == null
                ? R.string.budget_dialog_save
                : R.string.budget_dialog_update;

        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.requireContext())
                .setTitle(titleRes)
                .setView(dialogView)
                .setPositiveButton(positiveRes, null)
                .setNegativeButton(R.string.budget_dialog_cancel, null);
        if (existingRow != null) {
            // Expose delete as a neutral button so users don't need to know about long-press.
            builder.setNeutralButton(R.string.budget_delete_confirm, null);
        }
        AlertDialog dialog = builder.create();

        // setOnShowListener + null in setPositiveButton: standard pattern to prevent the
        // AlertDialog from auto-dismissing before date validation has a chance to show an error.
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String dateStr = SpinnerHelper.textOf(dateInput);
                LocalDate bookingDate;
                try {
                    bookingDate = LocalDate.parse(dateStr);
                } catch (DateTimeParseException ex) {
                    dateInput.setError(fragment.getString(R.string.budget_invalid_date));
                    return;
                }

                String amountStr = SpinnerHelper.textOf(amountInput);
                boolean selectedExpense = typeGroup.getCheckedButtonId() == R.id.BudgetDialogTypeExpense;
                String note = SpinnerHelper.textOf(noteInput);
                String categoryId = SpinnerHelper.idAtPosition(
                        categoriesForType(allCategories, selectedExpense),
                        categorySpinner.getSelectedItemPosition(), c -> c.id);
                String accountId = SpinnerHelper.idAtPosition(
                        activeAccounts(allAccounts),
                        accountSpinner.getSelectedItemPosition(), a -> a.id);

                if (accountId == null) return;

                if (existingRow == null) {
                    listener.onAddTransaction(amountStr, selectedExpense, categoryId,
                            note.isEmpty() ? null : note, bookingDate, accountId);
                } else {
                    listener.onUpdateTransaction(existingRow.getTransactionId(), amountStr,
                            selectedExpense, categoryId, note.isEmpty() ? null : note,
                            bookingDate, accountId);
                }
                dialog.dismiss();
            });

            if (existingRow != null) {
                Button deleteButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                if (deleteButton != null) {
                    deleteButton.setOnClickListener(v -> {
                        dialog.dismiss();
                        showDeleteConfirmation(existingRow);
                    });
                }
            }
        });

        dialog.show();
    }

    private static List<BudgetCategoryEntity> categoriesForType(List<BudgetCategoryEntity> allCategories,
                                                          boolean isExpense) {
        TransactionDirection dir = isExpense ? TransactionDirection.EXPENSE : TransactionDirection.INCOME;
        return allCategories.stream().filter(c -> c.direction == dir).collect(Collectors.toList());
    }

    private static List<BudgetAccountEntity> activeAccounts(List<BudgetAccountEntity> accounts) {
        return accounts.stream().filter(a -> !a.archived).collect(Collectors.toList());
    }

}
