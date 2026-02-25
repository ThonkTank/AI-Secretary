package com.autosecretary.features.budget.ui;

import android.content.ContentResolver;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.autosecretary.features.budget.domain.RecurringSuggestion;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class BudgetFragment extends Fragment {

    public static final String ARG_OPEN_ADD_TRANSACTION = "open_add_transaction";

    private BudgetViewModel budgetViewModel;
    private boolean shouldOpenAddTransactionDialog;
    private ActivityResultLauncher<String[]> csvPickerLauncher;
    private List<BudgetAccount> accountItems = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        csvPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null || budgetViewModel == null) return;
                    String fileName = getFileName(uri);
                    String mimeType = requireContext().getContentResolver().getType(uri);
                    try {
                        budgetViewModel.setImportStatus("Datei wird geladen: " + fileName);
                        byte[] bytes = readUriBytes(uri);
                        budgetViewModel.importFromCsv(fileName, bytes, mimeType);
                    } catch (IOException e) {
                        budgetViewModel.onImportReadFailed();
                    }
                }
        );
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
        return new BudgetOverviewViews(
                rootView.findViewById(R.id.BudgetTitle),
                rootView.findViewById(R.id.BudgetSummaryCard),
                rootView.findViewById(R.id.BudgetSummaryIncome),
                rootView.findViewById(R.id.BudgetSummaryExpense),
                rootView.findViewById(R.id.BudgetSummaryNet),
                rootView.findViewById(R.id.BudgetSummaryFreeBudget),
                rootView.findViewById(R.id.BudgetStatusMessage),
                rootView.findViewById(R.id.BudgetAddTransactionButton),
                rootView.findViewById(R.id.BudgetAddTransferButton),
                rootView.findViewById(R.id.BudgetImportStatementButton),
                rootView.findViewById(R.id.BudgetRetryButton),
                rootView.findViewById(R.id.BudgetTransactionList),
                rootView.findViewById(R.id.BudgetLoading),
                rootView.findViewById(R.id.BudgetMonthLabel),
                rootView.findViewById(R.id.BudgetMonthPrevButton),
                rootView.findViewById(R.id.BudgetMonthNextButton),
                rootView.findViewById(R.id.BudgetLimitBarsContainer),
                rootView.findViewById(R.id.BudgetSetLimitButton),
                rootView.findViewById(R.id.BudgetAccountSpinner),
                rootView.findViewById(R.id.BudgetRangeGroup),
                rootView.findViewById(R.id.BudgetBalanceChart)
        );
    }

    private void observeViewModel(@NonNull BudgetOverviewViews views) {
        budgetViewModel.getTitle().observe(getViewLifecycleOwner(), views.title::setText);
        budgetViewModel.getStatusMessage().observe(getViewLifecycleOwner(), views.status::setText);

        budgetViewModel.getSummaryData().observe(getViewLifecycleOwner(), data -> {
            if (data == null) return;
            views.summaryIncome.setText(String.format(Locale.GERMAN, "+%.2f €", data.getIncomeCents() / 100.0));
            views.summaryIncome.setTextColor(getColorFromResources(R.color.budget_positive));
            views.summaryExpense.setText(String.format(Locale.GERMAN, "-%.2f €", data.getExpenseCents() / 100.0));
            views.summaryExpense.setTextColor(getColorFromResources(R.color.budget_negative));
            long net = data.getNetCents();
            String sign = net >= 0 ? "+" : "-";
            views.summaryNet.setText(String.format(Locale.GERMAN, "%s%.2f €", sign, Math.abs(net) / 100.0));
            views.summaryNet.setTextColor(net >= 0
                    ? getColorFromResources(R.color.budget_positive)
                    : getColorFromResources(R.color.budget_negative));

            long freeBudget = data.getFreeBudgetCents();
            String freeBudgetSign = freeBudget >= 0 ? "+" : "-";
            views.summaryFreeBudget.setText(String.format(Locale.GERMAN, "%s%.2f €", freeBudgetSign, Math.abs(freeBudget) / 100.0));
            views.summaryFreeBudget.setTextColor(freeBudget >= 0
                    ? getColorFromResources(R.color.budget_positive)
                    : getColorFromResources(R.color.budget_negative));
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

        budgetViewModel.getImportResult().observe(getViewLifecycleOwner(), result -> {
            if (result != null && !result.recurringSuggestions().isEmpty()) {
                showRecurringSuggestionsDialog(result.recurringSuggestions());
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
            if (checkedId == R.id.BudgetRange30d) {
                budgetViewModel.setTimeRangeFilter(BudgetViewModel.TimeRangeFilter.DAYS_30);
            } else if (checkedId == R.id.BudgetRange3m) {
                budgetViewModel.setTimeRangeFilter(BudgetViewModel.TimeRangeFilter.MONTHS_3);
            } else if (checkedId == R.id.BudgetRange12m) {
                budgetViewModel.setTimeRangeFilter(BudgetViewModel.TimeRangeFilter.MONTHS_12);
            }
        });

        views.monthPrev.setOnClickListener(v -> budgetViewModel.navigateMonth(-1));
        views.monthNext.setOnClickListener(v -> budgetViewModel.navigateMonth(1));
        views.addTransaction.setOnClickListener(v -> showAddTransactionDialog());
        views.addTransfer.setOnClickListener(v -> showTransferDialog());
        views.importStatement.setOnClickListener(v ->
                csvPickerLauncher.launch(new String[]{"text/csv", "text/plain", "application/pdf", "*/*"}));
        views.retry.setOnClickListener(v -> budgetViewModel.retry());
        views.setLimitButton.setOnClickListener(v -> showEditLimitDialog(null, null, 0));
    }

    private void restoreDeferredActions(@NonNull View rootView) {
        if (shouldOpenAddTransactionDialog) {
            shouldOpenAddTransactionDialog = false;
            rootView.post(this::showAddTransactionDialog);
        }
    }

    private static class BudgetOverviewViews {
        private final TextView title;
        private final View summaryCard;
        private final TextView summaryIncome;
        private final TextView summaryExpense;
        private final TextView summaryNet;
        private final TextView summaryFreeBudget;
        private final TextView status;
        private final Button addTransaction;
        private final Button addTransfer;
        private final Button importStatement;
        private final Button retry;
        private final LinearLayout transactionList;
        private final ProgressBar loading;
        private final TextView monthLabel;
        private final ImageButton monthPrev;
        private final ImageButton monthNext;
        private final LinearLayout limitBarsContainer;
        private final Button setLimitButton;
        private final Spinner accountSpinner;
        private final RadioGroup rangeGroup;
        private final BudgetBalanceChartView chartView;

        private BudgetOverviewViews(TextView title,
                                    View summaryCard,
                                    TextView summaryIncome,
                                    TextView summaryExpense,
                                    TextView summaryNet,
                                    TextView summaryFreeBudget,
                                    TextView status,
                                    Button addTransaction,
                                    Button addTransfer,
                                    Button importStatement,
                                    Button retry,
                                    LinearLayout transactionList,
                                    ProgressBar loading,
                                    TextView monthLabel,
                                    ImageButton monthPrev,
                                    ImageButton monthNext,
                                    LinearLayout limitBarsContainer,
                                    Button setLimitButton,
                                    Spinner accountSpinner,
                                    RadioGroup rangeGroup,
                                    BudgetBalanceChartView chartView) {
            this.title = title;
            this.summaryCard = summaryCard;
            this.summaryIncome = summaryIncome;
            this.summaryExpense = summaryExpense;
            this.summaryNet = summaryNet;
            this.summaryFreeBudget = summaryFreeBudget;
            this.status = status;
            this.addTransaction = addTransaction;
            this.addTransfer = addTransfer;
            this.importStatement = importStatement;
            this.retry = retry;
            this.transactionList = transactionList;
            this.loading = loading;
            this.monthLabel = monthLabel;
            this.monthPrev = monthPrev;
            this.monthNext = monthNext;
            this.limitBarsContainer = limitBarsContainer;
            this.setLimitButton = setLimitButton;
            this.accountSpinner = accountSpinner;
            this.rangeGroup = rangeGroup;
            this.chartView = chartView;
        }
    }

    private void renderAccountSpinner(List<BudgetAccount> accounts, Spinner spinner) {
        accountItems = accounts != null ? accounts : new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (BudgetAccount account : accountItems) {
            names.add(account.name);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    // --- Add/Edit Transaction Dialog (with category + account + date) ---

    private void showAddTransactionDialog() {
        showTransactionDialog(null);
    }

    private void showEditTransactionDialog(BudgetViewModel.BudgetTransactionRow row) {
        showTransactionDialog(row);
    }

    private void showTransactionDialog(@Nullable BudgetViewModel.BudgetTransactionRow existingRow) {
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

        List<BudgetCategory> allCategories = budgetViewModel.getCategories().getValue();
        if (allCategories == null) allCategories = new ArrayList<>();
        List<BudgetCategory> allCats = allCategories;

        List<BudgetAccount> allAccounts =
                budgetViewModel.getAccounts().getValue();
        if (allAccounts == null) allAccounts = new ArrayList<>();
        final List<BudgetAccount> finalAllAccounts = allAccounts;

        boolean isExpense = existingRow == null || existingRow.isExpense();
        populateCategorySpinner(categorySpinner, allCats, isExpense);
        populateAccountSpinner(accountSpinner, finalAllAccounts);

        typeGroup.setOnCheckedChangeListener((group, checkedId) ->
                populateCategorySpinner(categorySpinner, allCats,
                        checkedId == R.id.BudgetDialogTypeExpense));

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
            setCategorySelection(categorySpinner, allCats, existingRow.isExpense(), existingRow.getCategoryId());
            setAccountSelection(accountSpinner, finalAllAccounts, existingRow.getAccountId());
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
                    String categoryId = getSelectedCategoryId(categorySpinner, allCats, selectedExpense);
                    String accountId = getSelectedAccountId(accountSpinner, finalAllAccounts);
                    LocalDate bookingDate = parseDateInput(dateInput.getText() != null
                            ? dateInput.getText().toString().trim() : "");

                    if (bookingDate == null || accountId == null) return;

                    if (existingRow == null) {
                        budgetViewModel.addTransaction(amountStr, selectedExpense, categoryId,
                                note.isEmpty() ? null : note, bookingDate);
                    } else {
                        budgetViewModel.updateTransaction(existingRow.getTransactionId(), amountStr,
                                selectedExpense, categoryId, note, bookingDate, accountId);
                    }
                })
                .setNegativeButton(R.string.budget_dialog_cancel, null)
                .show();
    }

    private void showTransferDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.budget_transfer_dialog, null);
        Spinner sourceAccountSpinner = dialogView.findViewById(R.id.BudgetTransferSourceAccount);
        Spinner targetAccountSpinner = dialogView.findViewById(R.id.BudgetTransferTargetAccount);
        TextInputEditText amountInput = dialogView.findViewById(R.id.BudgetTransferAmount);
        TextInputEditText dateInput = dialogView.findViewById(R.id.BudgetTransferDate);
        TextInputEditText noteInput = dialogView.findViewById(R.id.BudgetTransferNote);

        List<BudgetAccount> accounts = budgetViewModel.getAccounts().getValue();
        if (accounts == null || accounts.size() < 2) {
            new AlertDialog.Builder(requireContext())
                    .setMessage(R.string.budget_transfer_requires_two_accounts)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        List<String> accountNames = new ArrayList<>();
        for (BudgetAccount account : accounts) {
            accountNames.add(account.name);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, accountNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sourceAccountSpinner.setAdapter(adapter);
        targetAccountSpinner.setAdapter(adapter);
        if (accounts.size() > 1) {
            targetAccountSpinner.setSelection(1);
        }

        dateInput.setText(LocalDate.now().toString());

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.budget_transfer_title)
                .setView(dialogView)
                .setPositiveButton(R.string.budget_transfer_save, (dialog, which) -> {
                    int sourceIdx = sourceAccountSpinner.getSelectedItemPosition();
                    int targetIdx = targetAccountSpinner.getSelectedItemPosition();
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

                    if (sourceIdx < 0 || sourceIdx >= accounts.size()
                            || targetIdx < 0 || targetIdx >= accounts.size()) {
                        return;
                    }

                    budgetViewModel.addTransfer(
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

    private void populateCategorySpinner(Spinner spinner, List<BudgetCategory> allCategories,
                                         boolean isExpense) {
        String filterType = isExpense ? "EXPENSE" : "INCOME";
        List<String> names = new ArrayList<>();
        for (BudgetCategory cat : allCategories) {
            if (filterType.equals(cat.type)) {
                names.add(buildCategoryDisplayLabel(cat));
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private String getSelectedCategoryId(Spinner spinner, List<BudgetCategory> allCategories,
                                         boolean isExpense) {
        int position = spinner.getSelectedItemPosition();
        if (position < 0) return null;
        String filterType = isExpense ? "EXPENSE" : "INCOME";
        int index = 0;
        for (BudgetCategory cat : allCategories) {
            if (filterType.equals(cat.type)) {
                if (index == position) return cat.id;
                index++;
            }
        }
        return null;
    }

    private void setCategorySelection(Spinner spinner, List<BudgetCategory> allCategories,
                                      boolean isExpense, String categoryId) {
        if (categoryId == null) return;
        String filterType = isExpense ? "EXPENSE" : "INCOME";
        int index = 0;
        for (BudgetCategory cat : allCategories) {
            if (filterType.equals(cat.type)) {
                if (categoryId.equals(cat.id)) {
                    spinner.setSelection(index);
                    return;
                }
                index++;
            }
        }
    }

    private void populateAccountSpinner(Spinner spinner, List<BudgetAccount> accounts) {
        List<String> names = new ArrayList<>();
        for (BudgetAccount account : accounts) {
            if (!account.archived) {
                names.add(account.name);
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private String getSelectedAccountId(Spinner spinner, List<BudgetAccount> accounts) {
        int position = spinner.getSelectedItemPosition();
        if (position < 0) return null;
        int index = 0;
        for (BudgetAccount account : accounts) {
            if (!account.archived) {
                if (index == position) return account.id;
                index++;
            }
        }
        return null;
    }

    private void setAccountSelection(Spinner spinner, List<BudgetAccount> accounts, String accountId) {
        if (accountId == null) return;
        int index = 0;
        for (BudgetAccount account : accounts) {
            if (!account.archived) {
                if (accountId.equals(account.id)) {
                    spinner.setSelection(index);
                    return;
                }
                index++;
            }
        }
    }

    private LocalDate parseDateInput(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception ignored) {
            return null;
        }
    }

    // --- Budget Limit Bars ---
    private void renderLimitBars(List<BudgetViewModel.BudgetLimitBar> bars,
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

        for (BudgetViewModel.BudgetLimitBar bar : bars) {
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

    private void renderTransactions(List<BudgetViewModel.BudgetTransactionRow> rows,
                                    LinearLayout container) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        Map<String, String> categoryColorsByLabel = getCategoryColorsByDisplayLabel();

        for (BudgetViewModel.BudgetTransactionRow row : rows) {
            View rowView = inflater.inflate(R.layout.budget_transaction_item, container, false);
            TextView label = rowView.findViewById(R.id.BudgetTransactionLabel);
            TextView amount = rowView.findViewById(R.id.BudgetTransactionAmount);
            label.setText(row.getLabel());
            amount.setText(row.getAmount());
            String labelColorHex = row.getCategoryColorHex();
            if (!isValidColorHex(labelColorHex)) {
                labelColorHex = categoryColorsByLabel.get(row.getLabel());
            }
            if (isValidColorHex(labelColorHex)) {
                label.setTextColor(Color.parseColor(labelColorHex));
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

    private Map<String, String> getCategoryColorsByDisplayLabel() {
        Map<String, String> out = new HashMap<>();
        List<BudgetCategory> allCategories = budgetViewModel.getCategories().getValue();
        if (allCategories == null) return out;
        for (BudgetCategory category : allCategories) {
            out.put(buildCategoryDisplayLabel(category), category.colorHex);
        }
        return out;
    }

    private boolean isValidColorHex(String colorHex) {
        if (colorHex == null) return false;
        return Pattern.matches("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$", colorHex);
    }

    private int getColorFromResources(int colorResId) {
        return ContextCompat.getColor(requireContext(), colorResId);
    }

    private void showDeleteTransactionDialog(BudgetViewModel.BudgetTransactionRow row) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.budget_delete_title)
                .setMessage(R.string.budget_delete_message)
                .setPositiveButton(R.string.budget_delete_confirm, (dialog, which) ->
                        budgetViewModel.deleteTransaction(row.getTransactionId()))
                .setNegativeButton(R.string.budget_dialog_cancel, null)
                .show();
    }

    private void showRecurringSuggestionsDialog(List<RecurringSuggestion> suggestions) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.budget_recurring_suggestions_dialog, null);
        LinearLayout listContainer = dialogView.findViewById(R.id.BudgetRecurringSuggestionList);
        TextView selectionInfo = dialogView.findViewById(R.id.BudgetRecurringSelectionInfo);

        boolean[] selections = new boolean[suggestions.size()];
        for (int i = 0; i < selections.length; i++) {
            selections[i] = true;
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        List<CheckBox> checkBoxes = new ArrayList<>();

        for (int i = 0; i < suggestions.size(); i++) {
            RecurringSuggestion suggestion = suggestions.get(i);
            View row = inflater.inflate(R.layout.budget_recurring_suggestion_item, listContainer, false);

            CheckBox checkbox = row.findViewById(R.id.BudgetSuggestionCheckbox);
            TextView payee = row.findViewById(R.id.BudgetSuggestionPayee);
            TextView pattern = row.findViewById(R.id.BudgetSuggestionPattern);
            TextView count = row.findViewById(R.id.BudgetSuggestionCount);
            TextView confidence = row.findViewById(R.id.BudgetSuggestionConfidence);
            TextView amount = row.findViewById(R.id.BudgetSuggestionAmount);

            checkbox.setChecked(true);
            checkBoxes.add(checkbox);

            payee.setText(suggestion.displayPayee());
            pattern.setText(getPatternDescription(suggestion));
            count.setText(getString(R.string.budget_recurring_transactions_count,
                    suggestion.transactionIds().size()));
            confidence.setText(getString(R.string.budget_recurring_confidence,
                    suggestion.confidenceScore() * 100));
            amount.setText(String.format(Locale.GERMAN, "%.2f €",
                    Math.abs(suggestion.avgAmountCents()) / 100.0));

            amount.setTextColor(suggestion.avgAmountCents() >= 0
                    ? getColorFromResources(R.color.budget_positive)
                    : getColorFromResources(R.color.budget_negative));

            if (suggestion.confidenceScore() >= 0.7) {
                confidence.setTextColor(getColorFromResources(R.color.budget_positive));
            } else if (suggestion.confidenceScore() >= 0.5) {
                confidence.setTextColor(getColorFromResources(R.color.budget_warning));
            } else {
                confidence.setTextColor(getColorFromResources(R.color.budget_neutral));
            }

            listContainer.addView(row);
        }

        updateSelectionInfo(selectionInfo, selections);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.budget_recurring_title)
                .setView(dialogView)
                .setPositiveButton(getString(R.string.budget_recurring_create, countSelected(selections)),
                        null)
                .setNegativeButton(R.string.budget_recurring_skip, null)
                .create();

        dialog.setOnShowListener(d -> {
            Button createButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            createButton.setOnClickListener(v -> {
                List<RecurringSuggestion> selected = new ArrayList<>();
                for (int i = 0; i < suggestions.size(); i++) {
                    if (selections[i]) selected.add(suggestions.get(i));
                }
                if (!selected.isEmpty()) {
                    budgetViewModel.applyRecurringSuggestions(selected);
                }
                dialog.dismiss();
            });

            for (int i = 0; i < checkBoxes.size(); i++) {
                int index = i;
                View row = listContainer.getChildAt(i);
                row.setOnClickListener(rv -> {
                    selections[index] = !selections[index];
                    checkBoxes.get(index).setChecked(selections[index]);
                    updateSelectionInfo(selectionInfo, selections);
                    updateCreateButton(createButton, selections);
                });
            }
        });

        dialog.show();
    }

    private void updateSelectionInfo(TextView info, boolean[] selections) {
        info.setText(getString(R.string.budget_recurring_selection_info,
                countSelected(selections), selections.length));
    }

    private void updateCreateButton(Button button, boolean[] selections) {
        int count = countSelected(selections);
        button.setText(getString(R.string.budget_recurring_create, count));
        button.setEnabled(count > 0);
    }

    private int countSelected(boolean[] selections) {
        int count = 0;
        for (boolean sel : selections) {
            if (sel) {
                count++;
            }
        }
        return count;
    }

    private String getPatternDescription(RecurringSuggestion suggestion) {
        if (suggestion.suggestedType() == null) {
            return getString(R.string.budget_recurring_pattern_unknown);
        }
        return switch (suggestion.suggestedType()) {
            case MONTHLY_DAY -> getString(R.string.budget_recurring_pattern_monthly_day,
                    suggestion.suggestedValue());
            case MONTHLY_LAST -> getString(R.string.budget_recurring_pattern_monthly_last);
            case WEEKLY -> getString(R.string.budget_recurring_pattern_weekly,
                    getDayName(suggestion.suggestedDayOfWeek()));
            case INTERVAL -> getString(R.string.budget_recurring_pattern_interval,
                    suggestion.suggestedValue());
        };
    }

    private String getDayName(DayOfWeek dow) {
        if (dow == null) return "";
        return switch (dow) {
            case MONDAY -> "Mo";
            case TUESDAY -> "Di";
            case WEDNESDAY -> "Mi";
            case THURSDAY -> "Do";
            case FRIDAY -> "Fr";
            case SATURDAY -> "Sa";
            case SUNDAY -> "So";
        };
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

        List<BudgetCategory> allCategories = budgetViewModel.getCategories().getValue();
        if (allCategories == null) allCategories = new ArrayList<>();
        List<BudgetCategory> allCats = allCategories;

        populateCategorySpinner(categorySpinner, allCats, true);

        if (preSelectedCategoryId != null) {
            int index = 0;
            for (BudgetCategory cat : allCats) {
                if ("EXPENSE".equals(cat.type)) {
                    if (preSelectedCategoryId.equals(cat.id)) {
                        categorySpinner.setSelection(index);
                        break;
                    }
                    index++;
                }
            }
        }

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

                    String normalized = amountStr.replace(',', '.');
                    double amountEuros;
                    try {
                        amountEuros = Double.parseDouble(normalized);
                    } catch (NumberFormatException e) {
                        return;
                    }

                    String categoryId = getSelectedCategoryId(categorySpinner, allCats, true);
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

                    budgetViewModel.saveBudgetLimit(
                            categoryId,
                            amountEuros,
                            rolloverSwitch.isChecked(),
                            rolloverCarryoverCents);
                })
                .setNegativeButton(R.string.budget_dialog_cancel, null)
                .show();
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = requireContext().getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result != null ? result : "import.csv";
    }

    private byte[] readUriBytes(Uri uri) throws IOException {
        ContentResolver cr = requireContext().getContentResolver();
        try (InputStream is = cr.openInputStream(uri)) {
            if (is == null) throw new IOException("Dateistream konnte nicht geöffnet werden: " + uri);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        }
    }
}
