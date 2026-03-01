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
import com.google.android.material.button.MaterialButtonToggleGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.R;
import com.autosecretary.app.AppCompositionRoot;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.budget.data.entity.BudgetAccountEntity;
import com.autosecretary.features.budget.data.entity.BudgetCategoryEntity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.features.budget.ui.internal.BudgetBalanceChartView;
import com.autosecretary.features.budget.ui.internal.BudgetTransactionAdapter;
import com.autosecretary.features.budget.ui.internal.BudgetImportPickerController;
import com.autosecretary.features.budget.ui.internal.BudgetLimitDialogController;
import com.autosecretary.features.budget.ui.internal.BudgetRecurringSuggestionsDialogController;
import com.autosecretary.features.budget.ui.internal.BudgetTransactionDialogController;
import com.autosecretary.features.budget.ui.internal.BudgetTransferDialogController;
import com.autosecretary.features.budget.ui.internal.CurrencyFormatter;
import com.autosecretary.shared.ui.SpinnerHelper;
import com.autosecretary.features.budget.ui.state.BudgetLimitBar;
import com.autosecretary.features.budget.ui.state.BudgetUiState;
import com.autosecretary.features.budget.ui.state.TimeRangeFilter;
import com.autosecretary.features.budget.ui.state.BudgetTransactionRow;
import com.autosecretary.features.budget.ui.state.UiText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Main screen for the budget feature: displays the account balance chart, monthly income/expense
 * summary, transaction list, and category spending-limit progress bars for the selected account.
 *
 * <p>Reading guide:
 * <ol>
 *   <li>{@code onViewCreated} is the entry point — it wires the ViewModel, binds views, and starts
 *       observing LiveData.
 *   <li>{@code observeViewModel} maps each LiveData stream to its view update.
 *   <li>{@code setupUserActions} attaches button and spinner listeners.
 *   <li>Dialog lifecycle is delegated to dedicated {@code *DialogController} objects
 *       (see {@code internal/} package). Controllers are created in {@code onCreate} so that
 *       Android's {@code ActivityResultLauncher} registrations happen before {@code onStart}.
 * </ol>
 *
 * <p>To open the add-transaction dialog on launch (e.g. from the home-screen widget tap),
 * pass {@code ARG_OPEN_ADD_TRANSACTION = true} in the Fragment arguments.
 */
public class BudgetFragment extends Fragment {

    /**
     * Boolean Fragment argument. When {@code true}, the add-transaction dialog is shown
     * automatically after the view is created. Set by the home-screen widget's pending intent
     * via {@code BudgetWidgetProvider} when the user taps the "add" shortcut.
     */
    public static final String ARG_OPEN_ADD_TRANSACTION = "open_add_transaction";

    private BudgetViewModel budgetViewModel;
    private boolean shouldOpenAddTransactionDialog;
    private BudgetImportPickerController importPickerController;
    private BudgetTransferDialogController transferDialogController;
    private BudgetRecurringSuggestionsDialogController recurringSuggestionsDialogController;
    private BudgetTransactionDialogController transactionDialogController;
    private BudgetLimitDialogController limitDialogController;
    private List<BudgetAccountEntity> accountItems = new ArrayList<>();
    private BudgetTransactionAdapter transactionAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        importPickerController = new BudgetImportPickerController(this, new BudgetImportPickerController.Listener() {
            @Override
            public void onImportPicked(String fileName, byte[] bytes, String mimeType) {
                if (budgetViewModel == null) return;
                budgetViewModel.setImportStatus(getString(R.string.budget_status_file_loading, fileName));
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

        transactionDialogController = new BudgetTransactionDialogController(this,
                new BudgetTransactionDialogController.Listener() {
                    @Override
                    public void onAddTransaction(String amountStr, boolean isExpense,
                                                 String categoryId, String note,
                                                 java.time.LocalDate date, String accountId) {
                        if (budgetViewModel == null) return;
                        budgetViewModel.addTransaction(amountStr, isExpense, categoryId, note, date, accountId);
                    }

                    @Override
                    public void onUpdateTransaction(String transactionId, String amountStr,
                                                    boolean isExpense, String categoryId,
                                                    String note, java.time.LocalDate date,
                                                    String accountId) {
                        if (budgetViewModel == null) return;
                        budgetViewModel.updateTransaction(transactionId, amountStr, isExpense,
                                categoryId, note, date, accountId);
                    }

                    @Override
                    public void onDeleteTransaction(String transactionId) {
                        if (budgetViewModel == null) return;
                        budgetViewModel.deleteTransaction(transactionId);
                    }
                });

        limitDialogController = new BudgetLimitDialogController(this,
                (categoryId, amountStr, rolloverEnabled, rolloverCarryoverStr) -> {
                    if (budgetViewModel == null) return;
                    budgetViewModel.saveBudgetLimitFromString(categoryId, amountStr,
                            rolloverEnabled, rolloverCarryoverStr);
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
        BudgetViewModelFactory factory = compositionRoot.getBudgetViewModelFactory();
        budgetViewModel = new ViewModelProvider(this, factory).get(BudgetViewModel.class);
        Bundle args = getArguments();
        shouldOpenAddTransactionDialog = args != null && args.getBoolean(ARG_OPEN_ADD_TRANSACTION, false);

        BudgetOverviewViews views = bindViews(view);
        transactionAdapter = new BudgetTransactionAdapter(new BudgetTransactionAdapter.Listener() {
            @Override
            public void onTransactionClick(BudgetTransactionRow row) {
                showEditTransactionDialog(row);
            }

            @Override
            public void onTransactionLongClick(BudgetTransactionRow row) {
                transactionDialogController.showDeleteConfirmation(row);
            }
        });
        views.transactionList.setLayoutManager(new LinearLayoutManager(requireContext()));
        views.transactionList.setAdapter(transactionAdapter);
        observeViewModel(views);
        setupUserActions(views);
        restoreDeferredActions(view);
    }

    private BudgetOverviewViews bindViews(@NonNull View rootView) {
        BudgetOverviewViews v = new BudgetOverviewViews();
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
        v.monthLabel = rootView.findViewById(R.id.NavLabel);
        v.monthPrev = rootView.findViewById(R.id.NavPrev);
        v.monthNext = rootView.findViewById(R.id.NavNext);
        v.limitBarsContainer = rootView.findViewById(R.id.BudgetLimitBarsContainer);
        v.setLimitButton = rootView.findViewById(R.id.BudgetSetLimitButton);
        v.accountSpinner = rootView.findViewById(R.id.BudgetAccountSpinner);
        v.rangeGroup = rootView.findViewById(R.id.BudgetRangeGroup);
        v.chartView = rootView.findViewById(R.id.BudgetBalanceChart);
        return v;
    }

    private void observeViewModel(@NonNull BudgetOverviewViews views) {
        budgetViewModel.getStatusMessage().observe(getViewLifecycleOwner(),
                uiText -> views.status.setText(uiText.resolve(requireContext())));

        budgetViewModel.getSummaryData().observe(getViewLifecycleOwner(), data -> {
            if (data == null) return;
            views.summaryIncome.setText(CurrencyFormatter.eurosAlwaysSigned(data.getIncomeCents()));
            views.summaryIncome.setTextColor(ContextCompat.getColor(requireContext(), R.color.budget_positive));
            views.summaryExpense.setText(CurrencyFormatter.eurosAlwaysSigned(-data.getExpenseCents()));
            views.summaryExpense.setTextColor(ContextCompat.getColor(requireContext(), R.color.budget_negative));
            bindSignedAmount(views.summaryNet, data.getNetCents());
            bindSignedAmount(views.summaryFreeBudget, data.getFreeBudgetCents());
        });

        budgetViewModel.getTransactions().observe(getViewLifecycleOwner(),
                rows -> transactionAdapter.setItems(rows));

        budgetViewModel.getChartPoints().observe(getViewLifecycleOwner(), views.chartView::setPoints);

        budgetViewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            boolean isLoading = state == BudgetUiState.LOADING;
            boolean isError = state == BudgetUiState.ERROR;
            boolean isContent = state == BudgetUiState.CONTENT;
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
            SpinnerHelper.setSelection(views.accountSpinner, accountItems, selectedId, a -> a.id);
        });

        budgetViewModel.getTimeRangeFilter().observe(getViewLifecycleOwner(), filter -> {
            int checkedId = switch (filter) {
                case DAYS_30 -> R.id.BudgetRange30d;
                case MONTHS_3 -> R.id.BudgetRange3m;
                case MONTHS_12 -> R.id.BudgetRange12m;
            };
            if (views.rangeGroup.getCheckedButtonId() != checkedId) {
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

        views.rangeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            TimeRangeFilter filter = checkedId == R.id.BudgetRange30d ? TimeRangeFilter.DAYS_30
                    : checkedId == R.id.BudgetRange3m ? TimeRangeFilter.MONTHS_3
                    : checkedId == R.id.BudgetRange12m ? TimeRangeFilter.MONTHS_12
                    : null;
            if (filter != null) budgetViewModel.setTimeRangeFilter(filter);
        });

        views.monthPrev.setContentDescription(getString(R.string.budget_month_prev_desc));
        views.monthNext.setContentDescription(getString(R.string.budget_month_next_desc));
        views.monthPrev.setOnClickListener(v -> budgetViewModel.navigateMonth(-1));
        views.monthNext.setOnClickListener(v -> budgetViewModel.navigateMonth(1));
        views.addTransaction.setOnClickListener(v -> showAddTransactionDialog());
        views.addTransfer.setOnClickListener(v -> showTransferDialog());
        views.importStatement.setOnClickListener(v -> importPickerController.launchPicker());
        views.retry.setOnClickListener(v -> budgetViewModel.retry());
        views.setLimitButton.setOnClickListener(v -> showEditLimitDialog(null, 0));
    }

    private void restoreDeferredActions(@NonNull View rootView) {
        if (shouldOpenAddTransactionDialog) {
            shouldOpenAddTransactionDialog = false;
            // Deferred via post() — showing a dialog directly in onViewCreated can fail before
            // the fragment manager is fully attached to the window.
            rootView.post(this::showAddTransactionDialog);
        }
    }

    /**
     * Groups all view references for the budget overview into a single holder.
     * Avoids scattering individual {@code View} fields across the Fragment class and
     * makes it easy to see at a glance which views are bound in {@code bindViews}.
     * Lifetime matches the view (created in {@code onViewCreated}, not retained).
     */
    private static class BudgetOverviewViews {
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
        RecyclerView transactionList;
        ProgressBar loading;
        TextView monthLabel;
        ImageButton monthPrev;
        ImageButton monthNext;
        LinearLayout limitBarsContainer;
        Button setLimitButton;
        Spinner accountSpinner;
        MaterialButtonToggleGroup rangeGroup;
        BudgetBalanceChartView chartView;
    }

    private void renderAccountSpinner(List<BudgetAccountEntity> accounts, Spinner spinner) {
        accountItems = accounts != null ? accounts : new ArrayList<>();
        SpinnerHelper.bindList(spinner, accountItems, a -> a.name, requireContext());
    }

    // --- Transaction dialogs ---

    private void showAddTransactionDialog() {
        transactionDialogController.showAdd(
                categoriesValue(), accountsValue());
    }

    private void showEditTransactionDialog(BudgetTransactionRow row) {
        transactionDialogController.showEdit(row, categoriesValue(), accountsValue());
    }

    private void showTransferDialog() {
        transferDialogController.show(accountsValue());
    }

    private void showEditLimitDialog(@Nullable String preSelectedCategoryId, long baseLimitCents) {
        limitDialogController.show(preSelectedCategoryId, baseLimitCents, categoriesValue());
    }

    private List<BudgetCategoryEntity> categoriesValue() {
        List<BudgetCategoryEntity> cats = budgetViewModel.getCategories().getValue();
        return cats != null ? cats : new ArrayList<>();
    }

    private List<BudgetAccountEntity> accountsValue() {
        List<BudgetAccountEntity> accts = budgetViewModel.getAccounts().getValue();
        return accts != null ? accts : new ArrayList<>();
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
            android.widget.ProgressBar progress = row.findViewById(R.id.BudgetLimitBarProgress);

            name.setText(bar.getCategoryName());
            String spentLabel = CurrencyFormatter.eurosMagnitude(bar.getSpentCents())
                    + " / " + CurrencyFormatter.eurosMagnitude(bar.getEffectiveLimitCents());
            spentText.setText(spentLabel);
            int pct = bar.getPercentage();
            percentText.setText(String.format(Locale.GERMAN, "%d%%", pct));
            progress.setProgress(Math.min(pct, 100));
            row.setContentDescription(getString(R.string.budget_limit_bar_content_description,
                    bar.getCategoryName(), spentLabel, pct));

            // Color priority: a category's configured hex color always wins, because the user
            // deliberately chose it. Status-based colors (negative/warning/positive) are only
            // used as a fallback when no category color is configured.
            int color;
            if (CurrencyFormatter.isValidColorHex(bar.getCategoryColorHex())) {
                color = Color.parseColor(bar.getCategoryColorHex());
            } else if (pct > 100) {
                color = ContextCompat.getColor(requireContext(), R.color.budget_negative);
            } else if (pct >= 80) {
                color = ContextCompat.getColor(requireContext(), R.color.budget_warning);
            } else {
                color = ContextCompat.getColor(requireContext(), R.color.budget_positive);
            }
            progress.setProgressTintList(ColorStateList.valueOf(color));
            percentText.setTextColor(color);

            row.setOnClickListener(v ->
                    showEditLimitDialog(bar.getCategoryId(), bar.getBaseLimitCents()));
            container.addView(row);
        }
    }

    private void bindSignedAmount(TextView view, long cents) {
        view.setText(CurrencyFormatter.eurosAlwaysSigned(cents));
        view.setTextColor(ContextCompat.getColor(requireContext(),
                cents >= 0 ? R.color.budget_positive : R.color.budget_negative));
    }
}
