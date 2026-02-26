package com.autosecretary.features.budget.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.R;
import com.autosecretary.app.AppCompositionRoot;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.ui.internal.BudgetBalanceChartView;
import com.autosecretary.features.budget.ui.internal.BudgetImportPickerController;
import com.autosecretary.features.budget.ui.internal.BudgetRecurringSuggestionsDialogController;
import com.autosecretary.features.budget.ui.internal.BudgetTransferDialogController;
import com.autosecretary.features.budget.ui.internal.SpinnerHelper;
import com.autosecretary.features.budget.ui.state.BudgetLimitBar;
import com.autosecretary.features.budget.ui.state.BudgetTransactionRow;
import com.autosecretary.features.budget.ui.state.UiText;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class BudgetFragment extends Fragment {

    public static final String ARG_OPEN_ADD_TRANSACTION = "open_add_transaction";

    private BudgetViewModel budgetViewModel;
    private boolean shouldOpenAddTransactionDialog;
    private BudgetImportPickerController importPickerController;
    private BudgetTransferDialogController transferDialogController;
    private BudgetRecurringSuggestionsDialogController recurringSuggestionsDialogController;
    private List<BudgetAccount> accountItems = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        importPickerController = new BudgetImportPickerController(this, new BudgetImportPickerController.Listener() {
            @Override
            public void onImportPicked(String fileName, byte[] bytes, String mimeType) {
                if (budgetViewModel == null) return;
                budgetViewModel.setImportStatus("Datei wird geladen: " + fileName);
                budgetViewModel.importFromCsv(fileName, bytes, mimeType);
            }

            @Override
            public void onImportReadFailed() {
                if (budgetViewModel == null) return;
                budgetViewModel.onImportReadFailed();
            }
        });
        importPickerController.register();

        transferDialogController = new BudgetTransferDialogController(this,
                (sourceAccountId, targetAccountId, amount, bookingDate, note) -> {
                    if (budgetViewModel == null) return;
                    budgetViewModel.addTransfer(sourceAccountId, targetAccountId, amount, bookingDate, note);
                });

        recurringSuggestionsDialogController = new BudgetRecurringSuggestionsDialogController(this,
                suggestions -> {
                    if (budgetViewModel == null) return;
                    budgetViewModel.applyRecurringSuggestions(suggestions);
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.budget_overview_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AppCompositionRoot compositionRoot =
                AutoSecretaryApplication.from(requireContext()).getAppCompositionRoot();
        BudgetViewModelFactory factory = compositionRoot.createBudgetViewModelFactory();
        budgetViewModel = new ViewModelProvider(this, factory).get(BudgetViewModel.class);
        shouldOpenAddTransactionDialog = getArguments() != null
                && getArguments().getBoolean(ARG_OPEN_ADD_TRANSACTION, false);

        BudgetOverviewViews views = bindViews(view);
        observeViewModel(views);
        setupUserActions(views);
        restoreDeferredActions(view);
    }

    private BudgetOverviewViews bindViews(@NonNull View rootView) {
        BudgetOverviewViews v = new BudgetOverviewViews();
        v.title = rootView.findViewById(R.id.BudgetTitle);
        v.summaryCard = rootView.findViewById(R.id.BudgetSummaryCard);
        v.summaryIncome = rootView.findViewById(R.id.BudgetSummaryIncome);
        v.summaryExpense = rootView.findViewById(R.id.BudgetSummaryExpense);
        v.summaryNet = rootView.findViewById(R.id.BudgetSummaryNet);
        v.summaryFreeBudget = rootView.findViewById(R.id.BudgetSummaryFreeBudget);
        v.status = rootView.findViewById(R.id.BudgetStatusMessage);
        v.addTransaction = rootView.findViewById(R.id.BudgetAddTransactionButton);
        v.addTransfer = rootView.findViewById(R.id.BudgetAddTransferButton);
        v.importStatement = rootView.findViewById(R.id.BudgetImportStatementButton);
        v.retry = rootView.findViewById(R.id.BudgetRetryButton);
        v.transactionList = rootView.findViewById(R.id.BudgetTransactionList);
        v.loading = rootView.findViewById(R.id.BudgetLoading);
        v.monthLabel = rootView.findViewById(R.id.BudgetMonthLabel);
        v.monthPrev = rootView.findViewById(R.id.BudgetMonthPrevButton);
        v.monthNext = rootView.findViewById(R.id.BudgetMonthNextButton);
        v.limitBarsContainer = rootView.findViewById(R.id.BudgetLimitBarsContainer);
        v.setLimitButton = rootView.findViewById(R.id.BudgetSetLimitButton);
        v.accountSpinner = rootView.findViewById(R.id.BudgetAccountSpinner);
        v.rangeGroup = rootView.findViewById(R.id.BudgetRangeGroup);
        v.chartView = rootView.findViewById(R.id.BudgetBalanceChart);
        return v;
    }

    private void observeViewModel(@NonNull BudgetOverviewViews views) {
        views.title.setText(R.string.budget_title);
        budgetViewModel.getStatusMessage().observe(getViewLifecycleOwner(),
                uiText -> views.status.setText(uiText.resolve(requireContext())));

        budgetViewModel.getSummaryData().observe(getViewLifecycleOwner(), data -> {
            if (data == null) return;
            views.summaryIncome.setText(String.format(Locale.GERMAN, "+%.2f €", data.getIncomeCents() / 100.0));
            views.summaryIncome.setTextColor(getColorFromResources(R.color.budget_positive));
            views.summaryExpense.setText(String.format(Locale.GERMAN, "-%.2f €", data.getExpenseCents() / 100.0));
            views.summaryExpense.setTextColor(getColorFromResources(R.color.budget_negative));
            bindSignedAmount(views.summaryNet, data.getNetCents());
            bindSignedAmount(views.summaryFreeBudget, data.getFreeBudgetCents());
        });

        budgetViewModel.getTransactions().observe(getViewLifecycleOwner(),
                rows -> renderTransactions(rows, views.transactionList));

        budgetViewModel.getChartPoints().observe(getViewLifecycleOwner(), views.chartView::setPoints);

        budgetViewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            boolean isLoading = state == BudgetViewModel.BudgetUiState.LOADING;
            boolean isError = state == BudgetViewModel.BudgetUiState.ERROR;
            boolean isContent = state == BudgetViewModel.BudgetUiState.CONTENT;
            views.loading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            views.retry.setVisibility(isError ? View.VISIBLE : View.GONE);
            views.transactionList.setVisibility(isContent ? View.VISIBLE : View.GONE);
            views.summaryCard.setVisibility(isContent ? View.VISIBLE : View.GONE);
        });

        budgetViewModel.getCurrentMonth().observe(getViewLifecycleOwner(), month ->
                views.monthLabel.setText(budgetViewModel.formatMonth(month)));

        budgetViewModel.getLimits().observe(getViewLifecycleOwner(),
                bars -> renderLimitBars(bars, views.limitBarsContainer, views.setLimitButton));

        budgetViewModel.getAccounts().observe(getViewLifecycleOwner(), accounts ->
                renderAccountSpinner(accounts, views.accountSpinner));

        budgetViewModel.getSelectedAccountId().observe(getViewLifecycleOwner(), selectedId -> {
            if (selectedId == null || accountItems.isEmpty()) return;
            for (int i = 0; i < accountItems.size(); i++) {
                if (selectedId.equals(accountItems.get(i).id) && views.accountSpinner.getSelectedItemPosition() != i) {
                    views.accountSpinner.setSelection(i, false);
                    break;
                }
            }
        });

        budgetViewModel.getTimeRangeFilter().observe(getViewLifecycleOwner(), filter -> {
            int checkedId = switch (filter) {
                case DAYS_30 -> R.id.BudgetRange30d;
                case MONTHS_3 -> R.id.BudgetRange3m;
                case MONTHS_12 -> R.id.BudgetRange12m;
            };
            if (views.rangeGroup.getCheckedRadioButtonId() != checkedId) {
                views.rangeGroup.check(checkedId);
            }
        });

        budgetViewModel.getImportSuggestions().observe(getViewLifecycleOwner(), suggestions -> {
            if (suggestions != null && !suggestions.isEmpty()) {
                recurringSuggestionsDialogController.show(suggestions);
                budgetViewModel.clearImportResult();
            }
        });
    }

    private void setupUserActions(@NonNull BudgetOverviewViews views) {
        views.accountSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                if (position >= 0 && position < accountItems.size()) {
                    budgetViewModel.setSelectedAccount(accountItems.get(position).id);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        views.rangeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            TimeRangeFilter filter = null;
            if (checkedId == R.id.BudgetRange30d) {
                filter = TimeRangeFilter.DAYS_30;
            } else if (checkedId == R.id.BudgetRange3m) {
                filter = TimeRangeFilter.MONTHS_3;
            } else if (checkedId == R.id.BudgetRange12m) {
                filter = TimeRangeFilter.MONTHS_12;
            }
            if (filter != null) budgetViewModel.setTimeRangeFilter(filter);
        });

        views.monthPrev.setOnClickListener(v -> budgetViewModel.navigateMonth(-1));
        views.monthNext.setOnClickListener(v -> budgetViewModel.navigateMonth(1));
        views.addTransaction.setOnClickListener(v -> showAddTransactionDialog());
        views.addTransfer.setOnClickListener(v -> showTransferDialog());
        views.importStatement.setOnClickListener(v -> importPickerController.launchPicker());
        views.retry.setOnClickListener(v -> budgetViewModel.retry());
        views.setLimitButton.setOnClickListener(v -> showEditLimitDialog(null, null, 0));
    }

    private void restoreDeferredActions(@NonNull View rootView) {
        if (shouldOpenAddTransactionDialog) {
            shouldOpenAddTransactionDialog = false;
            // Deferred via post() — showing a dialog directly in onViewCreated can fail before
            // the fragment manager is fully attached to the window.
            rootView.post(this::showAddTransactionDialog);
        }
    }

    private static class BudgetOverviewViews {
        TextView title;
        View summaryCard;
        TextView summaryIncome;
        TextView summaryExpense;
        TextView summaryNet;
        TextView summaryFreeBudget;
        TextView status;
        Button addTransaction;
        Button addTransfer;
        Button importStatement;
        Button retry;
        LinearLayout transactionList;
        ProgressBar loading;
        TextView monthLabel;
        ImageButton monthPrev;
        ImageButton monthNext;
        LinearLayout limitBarsContainer;
        Button setLimitButton;
        Spinner accountSpinner;
        RadioGroup rangeGroup;
        BudgetBalanceChartView chartView;
    }

    private void renderAccountSpinner(List<BudgetAccount> accounts, Spinner spinner) {
        accountItems = accounts != null ? accounts : new ArrayList<>();
        SpinnerHelper.bindList(spinner, accountItems, a -> a.name, requireContext());
    }

    // --- Add/Edit Transaction Dialog (with category + account + date) ---

    private void showAddTransactionDialog() {
        showTransactionDialog(null);
    }

    private void showEditTransactionDialog(BudgetTransactionRow row) {
        showTransactionDialog(row);
    }

    private void showTransactionDialog(@Nullable BudgetTransactionRow existingRow) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.budget_add_transaction_dialog, null);
        TextInputEditText amountInput = dialogView.findViewById(R.id.BudgetDialogAmount);
        RadioButton expenseRadio = dialogView.findViewById(R.id.BudgetDialogTypeExpense);
        RadioButton incomeRadio = dialogView.findViewById(R.id.BudgetDialogTypeIncome);
        RadioGroup typeGroup = dialogView.findViewById(R.id.BudgetDialogTypeGroup);
        Spinner categorySpinner = dialogView.findViewById(R.id.BudgetDialogCategory);
        TextInputEditText noteInput = dialogView.findViewById(R.id.BudgetDialogNote);
        TextInputEditText dateInput = dialogView.findViewById(R.id.BudgetDialogDate);
        Spinner accountSpinner = dialogView.findViewById(R.id.BudgetDialogAccount);

        List<BudgetCategory> cats = budgetViewModel.getCategories().getValue();
        final List<BudgetCategory> allCategories = cats != null ? cats : new ArrayList<>();

        List<BudgetAccount> accts = budgetViewModel.getAccounts().getValue();
        final List<BudgetAccount> allAccounts = accts != null ? accts : new ArrayList<>();

        boolean isExpense = existingRow == null || existingRow.isExpense();
        SpinnerHelper.bindList(categorySpinner, categoriesForType(allCategories, isExpense),
                this::buildCategoryDisplayLabel, requireContext());
        SpinnerHelper.bindList(accountSpinner, activeAccounts(allAccounts), a -> a.name, requireContext());

        typeGroup.setOnCheckedChangeListener((group, checkedId) ->
                SpinnerHelper.bindList(categorySpinner,
                        categoriesForType(allCategories, checkedId == R.id.BudgetDialogTypeExpense),
                        this::buildCategoryDisplayLabel, requireContext()));

        LocalDate selectedDate = existingRow != null && existingRow.getBookingDate() != null
                ? existingRow.getBookingDate() : LocalDate.now();

        if (existingRow != null) {
            amountInput.setText(String.format(Locale.GERMAN, "%.2f",
                    Math.abs(existingRow.getAmountCents()) / 100.0));
            noteInput.setText(existingRow.getNote() != null ? existingRow.getNote() : "");
            if (existingRow.isExpense()) {
                expenseRadio.setChecked(true);
            } else {
                incomeRadio.setChecked(true);
            }
            SpinnerHelper.setSelection(categorySpinner, categoriesForType(allCategories, existingRow.isExpense()),
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

        new AlertDialog.Builder(requireContext())
                .setTitle(titleRes)
                .setView(dialogView)
                .setPositiveButton(positiveRes, (dialog, which) -> {
                    String amountStr = amountInput.getText() != null
                            ? amountInput.getText().toString().trim() : "";
                    boolean selectedExpense = expenseRadio.isChecked();
                    String note = noteInput.getText() != null
                            ? noteInput.getText().toString().trim() : "";
                    String categoryId = SpinnerHelper.idAtPosition(
                            categoriesForType(allCategories, selectedExpense),
                            categorySpinner.getSelectedItemPosition(), c -> c.id);
                    String accountId = SpinnerHelper.idAtPosition(
                            activeAccounts(allAccounts),
                            accountSpinner.getSelectedItemPosition(), a -> a.id);
                    LocalDate bookingDate = parseDateInput(dateInput.getText() != null
                            ? dateInput.getText().toString().trim() : "");

                    if (accountId == null) return;

                    if (existingRow == null) {
                        budgetViewModel.addTransaction(amountStr, selectedExpense, categoryId,
                                note.isEmpty() ? null : note, bookingDate, accountId);
                    } else {
                        budgetViewModel.updateTransaction(existingRow.getTransactionId(), amountStr,
                                selectedExpense, categoryId, note, bookingDate, accountId);
                    }
                })
                .setNegativeButton(R.string.budget_dialog_cancel, null)
                .show();
    }

    private void showTransferDialog() {
        if (transferDialogController == null || budgetViewModel == null) return;
        transferDialogController.show(budgetViewModel.getAccounts().getValue());
    }

    private List<BudgetCategory> categoriesForType(List<BudgetCategory> allCategories,
                                                   boolean isExpense) {
        BudgetTransactionEntity.TransactionType filterType = isExpense
                ? BudgetTransactionEntity.TransactionType.EXPENSE
                : BudgetTransactionEntity.TransactionType.INCOME;
        List<BudgetCategory> filtered = new ArrayList<>();
        for (BudgetCategory category : allCategories) {
            if (filterType.name().equals(category.type)) {
                filtered.add(category);
            }
        }
        return filtered;
    }

    private List<BudgetAccount> activeAccounts(List<BudgetAccount> accounts) {
        List<BudgetAccount> active = new ArrayList<>();
        for (BudgetAccount account : accounts) {
            if (!account.archived) {
                active.add(account);
            }
        }
        return active;
    }

    private LocalDate parseDateInput(String dateStr) {
        if (dateStr != null && !dateStr.isEmpty()) {
            try {
                return LocalDate.parse(dateStr);
            } catch (Exception ignored) {
            }
        }
        return LocalDate.now();
    }

    // --- Budget Limit Bars ---
    private void renderLimitBars(List<BudgetLimitBar> bars,
                                 LinearLayout container, Button setLimitButton) {
        container.removeAllViews();
        if (bars == null || bars.isEmpty()) {
            container.setVisibility(View.GONE);
            setLimitButton.setVisibility(View.VISIBLE);
            return;
        }
        container.setVisibility(View.VISIBLE);
        setLimitButton.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(container.getContext());

        for (BudgetLimitBar bar : bars) {
            View row = inflater.inflate(R.layout.budget_limit_bar_item, container, false);
            TextView name = row.findViewById(R.id.BudgetLimitBarName);
            TextView spentText = row.findViewById(R.id.BudgetLimitBarSpentText);
            TextView percentText = row.findViewById(R.id.BudgetLimitBarPercent);
            ProgressBar progress = row.findViewById(R.id.BudgetLimitBarProgress);

            name.setText(bar.getCategoryName());
            spentText.setText(String.format(Locale.GERMAN, "%.2f / %.2f €",
                    bar.getSpentCents() / 100.0, bar.getEffectiveLimitEuros()));
            int pct = bar.getPercentage();
            percentText.setText(String.format(Locale.GERMAN, "%d%%", pct));
            progress.setProgress(Math.min(pct, 100));

            int color;
            if (isValidColorHex(bar.getCategoryColorHex())) {
                color = Color.parseColor(bar.getCategoryColorHex());
            } else if (pct > 100) {
                color = getColorFromResources(R.color.budget_negative);
            } else if (pct >= 80) {
                color = getColorFromResources(R.color.budget_warning);
            } else {
                color = getColorFromResources(R.color.budget_positive);
            }
            progress.setProgressTintList(ColorStateList.valueOf(color));
            percentText.setTextColor(color);

            row.setOnClickListener(v ->
                    showEditLimitDialog(bar.getCategoryId(), bar.getCategoryName(), bar.getBaseLimitEuros()));
            container.addView(row);
        }
    }

    private void renderTransactions(List<BudgetTransactionRow> rows,
                                    LinearLayout container) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(container.getContext());

        for (BudgetTransactionRow row : rows) {
            View rowView = inflater.inflate(R.layout.budget_transaction_item, container, false);
            TextView label = rowView.findViewById(R.id.BudgetTransactionLabel);
            TextView amount = rowView.findViewById(R.id.BudgetTransactionAmount);
            label.setText(row.getLabel());
            amount.setText(row.getAmount());
            if (isValidColorHex(row.getCategoryColorHex())) {
                label.setTextColor(Color.parseColor(row.getCategoryColorHex()));
            }
            amount.setTextColor(row.isExpense()
                    ? getColorFromResources(R.color.budget_negative)
                    : getColorFromResources(R.color.budget_positive));
            rowView.setContentDescription(
                    getString(R.string.budget_transaction_content_description,
                            row.getLabel(), row.getAmount()));

            rowView.setOnClickListener(v -> showEditTransactionDialog(row));

            rowView.setOnLongClickListener(v -> {
                showDeleteTransactionDialog(row);
                return true;
            });

            container.addView(rowView);
        }
    }

    private String buildCategoryDisplayLabel(BudgetCategory category) {
        String icon = (category.icon == null || category.icon.trim().isEmpty())
                ? BudgetCategory.DEFAULT_ICON
                : category.icon.trim();
        return icon + " " + category.name;
    }

    private boolean isValidColorHex(String colorHex) {
        if (colorHex == null) return false;
        return Pattern.matches("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$", colorHex);
    }

    private void bindSignedAmount(TextView view, long cents) {
        String sign = cents >= 0 ? "+" : "-";
        view.setText(String.format(Locale.GERMAN, "%s%.2f €", sign, Math.abs(cents) / 100.0));
        view.setTextColor(getColorFromResources(
                cents >= 0 ? R.color.budget_positive : R.color.budget_negative));
    }

    private int getColorFromResources(int colorResId) {
        return ContextCompat.getColor(requireContext(), colorResId);
    }

    private void showDeleteTransactionDialog(BudgetTransactionRow row) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.budget_delete_title)
                .setMessage(R.string.budget_delete_message)
                .setPositiveButton(R.string.budget_delete_confirm, (dialog, which) ->
                        budgetViewModel.deleteTransaction(row.getTransactionId()))
                .setNegativeButton(R.string.budget_dialog_cancel, null)
                .show();
    }

    private void showEditLimitDialog(String preSelectedCategoryId,
                                     String preSelectedCategoryName,
                                     double currentAmount) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.budget_edit_limit_dialog, null);
        Spinner categorySpinner = dialogView.findViewById(R.id.BudgetLimitDialogCategory);
        TextInputEditText amountInput = dialogView.findViewById(R.id.BudgetLimitDialogAmount);
        com.google.android.material.switchmaterial.SwitchMaterial rolloverSwitch =
                dialogView.findViewById(R.id.BudgetLimitDialogRolloverEnabled);
        TextInputEditText rolloverCarryoverInput =
                dialogView.findViewById(R.id.BudgetLimitDialogRolloverCarryover);

        List<BudgetCategory> cats = budgetViewModel.getCategories().getValue();
        final List<BudgetCategory> allCategories = cats != null ? cats : new ArrayList<>();

        SpinnerHelper.bindList(categorySpinner, categoriesForType(allCategories, true),
                this::buildCategoryDisplayLabel, requireContext());
        SpinnerHelper.setSelection(categorySpinner, categoriesForType(allCategories, true),
                preSelectedCategoryId, c -> c.id);

        if (currentAmount > 0) {
            amountInput.setText(String.format(Locale.GERMAN, "%.2f", currentAmount));
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.budget_edit_limit_title)
                .setView(dialogView)
                .setPositiveButton(R.string.budget_limit_save, (dialog, which) -> {
                    String amountStr = amountInput.getText() != null
                            ? amountInput.getText().toString().trim() : "";
                    if (amountStr.isEmpty()) return;

                    String categoryId = SpinnerHelper.idAtPosition(categoriesForType(allCategories, true),
                            categorySpinner.getSelectedItemPosition(), c -> c.id);
                    if (categoryId == null) return;

                    String rolloverCarryoverStr = rolloverCarryoverInput.getText() != null
                            ? rolloverCarryoverInput.getText().toString().trim() : "";
                    long rolloverCarryoverCents = 0L;
                    if (!rolloverCarryoverStr.isEmpty()) {
                        try {
                            rolloverCarryoverCents = Math.round(Double.parseDouble(rolloverCarryoverStr.replace(',', '.')) * 100.0);
                        } catch (NumberFormatException e) {
                            return;
                        }
                    }

                    budgetViewModel.saveBudgetLimitFromString(
                            categoryId,
                            amountStr,
                            rolloverSwitch.isChecked(),
                            rolloverCarryoverCents);
                })
                .setNegativeButton(R.string.budget_dialog_cancel, null)
                .show();
    }

}
