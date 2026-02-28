package com.autosecretary.features.budget.ui.internal;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.ui.state.BudgetTransactionRow;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    public void showAdd(List<BudgetCategory> allCategories, List<BudgetAccount> allAccounts) {
        show(null, allCategories, allAccounts);
    }

    public void showEdit(BudgetTransactionRow existingRow,
                         List<BudgetCategory> allCategories,
                         List<BudgetAccount> allAccounts) {
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
                      List<BudgetCategory> allCategories,
                      List<BudgetAccount> allAccounts) {
        View dialogView = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.budget_add_transaction_dialog, null);
        TextInputEditText amountInput = dialogView.findViewById(R.id.BudgetDialogAmount);
        RadioButton expenseRadio = dialogView.findViewById(R.id.BudgetDialogTypeExpense);
        RadioButton incomeRadio = dialogView.findViewById(R.id.BudgetDialogTypeIncome);
        RadioGroup typeGroup = dialogView.findViewById(R.id.BudgetDialogTypeGroup);
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

        typeGroup.setOnCheckedChangeListener((group, checkedId) ->
                SpinnerHelper.bindList(categorySpinner,
                        categoriesForType(allCategories, checkedId == R.id.BudgetDialogTypeExpense),
                        c -> BudgetSummaryPresentationMapper.categoryLabel(c.icon, c.name),
                        fragment.requireContext()));

        LocalDate selectedDate = existingRow != null && existingRow.getBookingDate() != null
                ? existingRow.getBookingDate() : LocalDate.now();

        if (existingRow != null) {
            amountInput.setText(String.format(Locale.GERMAN, "%.2f",
                    Math.abs(existingRow.getAmountCents()) / 100.0));
            noteInput.setText(existingRow.getNote() != null ? existingRow.getNote() : "");
            expenseRadio.setChecked(existingRow.isExpense());
            incomeRadio.setChecked(!existingRow.isExpense());
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

        AlertDialog dialog = new AlertDialog.Builder(fragment.requireContext())
                .setTitle(titleRes)
                .setView(dialogView)
                .setPositiveButton(positiveRes, null)
                .setNegativeButton(R.string.budget_dialog_cancel, null)
                .create();

        // setOnShowListener + null in setPositiveButton: standard pattern to prevent the
        // AlertDialog from auto-dismissing before date validation has a chance to show an error.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String dateStr = SpinnerHelper.textOf(dateInput);
            LocalDate bookingDate;
            try {
                bookingDate = LocalDate.parse(dateStr);
            } catch (DateTimeParseException ex) {
                dateInput.setError(fragment.getString(R.string.budget_transfer_invalid_date));
                return;
            }

            String amountStr = SpinnerHelper.textOf(amountInput);
            boolean selectedExpense = expenseRadio.isChecked();
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
        }));

        dialog.show();
    }

    private static List<BudgetCategory> categoriesForType(List<BudgetCategory> allCategories,
                                                          boolean isExpense) {
        TransactionDirection filterType = isExpense
                ? TransactionDirection.EXPENSE
                : TransactionDirection.INCOME;
        List<BudgetCategory> filtered = new ArrayList<>();
        for (BudgetCategory category : allCategories) {
            if (filterType == category.direction) {
                filtered.add(category);
            }
        }
        return filtered;
    }

    private static List<BudgetAccount> activeAccounts(List<BudgetAccount> accounts) {
        List<BudgetAccount> active = new ArrayList<>();
        for (BudgetAccount account : accounts) {
            if (!account.archived) {
                active.add(account);
            }
        }
        return active;
    }

}
