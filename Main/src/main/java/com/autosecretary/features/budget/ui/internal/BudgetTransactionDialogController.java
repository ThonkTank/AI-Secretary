package com.autosecretary.features.budget.ui.internal;

import android.content.Context;
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
import com.autosecretary.features.budget.domain.AmountParser;
import com.autosecretary.shared.ui.DialogHelper;
import com.autosecretary.shared.ui.DialogValidation;
import com.autosecretary.shared.ui.SimpleButtonCheckedListener;
import com.autosecretary.shared.ui.SpinnerHelper;
import com.autosecretary.features.budget.data.entity.BudgetCategoryEntity;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.ui.state.BudgetTransactionRow;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
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
        DialogHelper.showDeleteConfirmation(fragment.requireContext(),
                R.string.budget_delete_title, R.string.budget_delete_message,
                () -> listener.onDeleteTransaction(row.transactionId()));
    }

    private void show(@Nullable BudgetTransactionRow existingRow,
                      List<BudgetCategoryEntity> allCategories,
                      List<BudgetAccountEntity> allAccounts) {
        Context ctx = fragment.requireContext();
        View dialogView = LayoutInflater.from(ctx)
                .inflate(R.layout.budget_add_transaction_dialog, null);
        TextInputEditText amountInput = dialogView.findViewById(R.id.BudgetDialogAmount);
        MaterialButtonToggleGroup typeGroup = dialogView.findViewById(R.id.BudgetDialogTypeGroup);
        Spinner categorySpinner = dialogView.findViewById(R.id.BudgetDialogCategory);
        TextInputEditText noteInput = dialogView.findViewById(R.id.BudgetDialogNote);
        TextInputEditText dateInput = dialogView.findViewById(R.id.BudgetDialogDate);
        Spinner accountSpinner = dialogView.findViewById(R.id.BudgetDialogAccount);

        List<BudgetCategoryEntity> expenseCategories = BudgetSummaryPresentationMapper
                .categoriesForDirection(allCategories, TransactionDirection.EXPENSE);
        List<BudgetCategoryEntity> incomeCategories = BudgetSummaryPresentationMapper
                .categoriesForDirection(allCategories, TransactionDirection.INCOME);
        List<BudgetAccountEntity> accounts = activeAccounts(allAccounts);

        boolean isExpense = existingRow == null || existingRow.isExpense();
        SpinnerHelper.bindList(categorySpinner, isExpense ? expenseCategories : incomeCategories,
                c -> BudgetSummaryPresentationMapper.categoryLabel(c.icon, c.name), ctx);
        SpinnerHelper.bindList(accountSpinner, accounts, a -> a.name, ctx);

        typeGroup.addOnButtonCheckedListener(new SimpleButtonCheckedListener() {
            @Override
            public void onChecked(MaterialButtonToggleGroup group, int checkedId) {
                SpinnerHelper.bindList(categorySpinner,
                        checkedId == R.id.BudgetDialogTypeExpense ? expenseCategories : incomeCategories,
                        c -> BudgetSummaryPresentationMapper.categoryLabel(c.icon, c.name), ctx);
            }
        });

        LocalDate selectedDate = existingRow != null && existingRow.bookingDate() != null
                ? existingRow.bookingDate() : LocalDate.now();

        if (existingRow != null) {
            amountInput.setText(CurrencyFormatter.centsToDecimal(existingRow.amountCents()));
            noteInput.setText(Objects.toString(existingRow.note(), ""));
            typeGroup.check(existingRow.isExpense()
                    ? R.id.BudgetDialogTypeExpense : R.id.BudgetDialogTypeIncome);
            SpinnerHelper.setSelection(categorySpinner,
                    existingRow.isExpense() ? expenseCategories : incomeCategories,
                    existingRow.categoryId(), c -> c.id);
            SpinnerHelper.setSelection(accountSpinner, accounts,
                    existingRow.accountId(), a -> a.id);
        }

        dateInput.setText(selectedDate.toString());
        DialogHelper.setupDatePicker(dateInput, ctx);

        int titleRes = existingRow == null
                ? R.string.budget_dialog_title
                : R.string.budget_dialog_edit_title;
        int positiveRes = existingRow == null
                ? R.string.budget_dialog_save
                : R.string.budget_dialog_update;

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
                .setTitle(titleRes)
                .setView(dialogView)
                .setPositiveButton(positiveRes, null)
                .setNegativeButton(R.string.action_cancel, null);
        if (existingRow != null) {
            // Expose delete as a neutral button so users don't need to know about long-press.
            builder.setNeutralButton(R.string.action_delete, null);
        }
        AlertDialog dialog = builder.create();

        DialogHelper.showWithValidation(dialog, () -> {
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
            boolean selectedExpense = typeGroup.getCheckedButtonId() == R.id.BudgetDialogTypeExpense;
            String note = DialogValidation.textOfNullable(noteInput);
            String categoryId = SpinnerHelper.idAtPosition(
                    selectedExpense ? expenseCategories : incomeCategories,
                    categorySpinner.getSelectedItemPosition(), c -> c.id);
            String accountId = SpinnerHelper.idAtPosition(
                    accounts,
                    accountSpinner.getSelectedItemPosition(), a -> a.id);

            if (accountId == null) return;

            if (existingRow == null) {
                listener.onAddTransaction(amountStr, selectedExpense, categoryId,
                        note, bookingDate, accountId);
            } else {
                listener.onUpdateTransaction(existingRow.transactionId(), amountStr,
                        selectedExpense, categoryId, note,
                        bookingDate, accountId);
            }
            dialog.dismiss();
        }, existingRow != null ? dlg -> {
            Button deleteButton = dlg.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (deleteButton != null) {
                deleteButton.setOnClickListener(v -> {
                    dlg.dismiss();
                    showDeleteConfirmation(existingRow);
                });
            }
        } : null);
    }

    private static List<BudgetAccountEntity> activeAccounts(List<BudgetAccountEntity> accounts) {
        return accounts.stream().filter(a -> !a.archived).collect(Collectors.toList());
    }

}
