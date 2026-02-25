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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.R;
import com.autosecretary.app.AppCompositionRoot;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.budget.data.BudgetAccount;
import com.autosecretary.features.budget.data.BudgetCategory;
import com.autosecretary.features.budget.domain.RecurringSuggestion;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BudgetFragment extends Fragment {

    private BudgetViewModel budgetViewModel;
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

        TextView title = view.findViewById(R.id.BudgetTitle);
        View summaryCard = view.findViewById(R.id.BudgetSummaryCard);
        TextView summaryIncome = view.findViewById(R.id.BudgetSummaryIncome);
        TextView summaryExpense = view.findViewById(R.id.BudgetSummaryExpense);
        TextView summaryNet = view.findViewById(R.id.BudgetSummaryNet);
        TextView status = view.findViewById(R.id.BudgetStatusMessage);
        Button addTransaction = view.findViewById(R.id.BudgetAddTransactionButton);
        Button addTransfer = view.findViewById(R.id.BudgetAddTransferButton);
        Button importStatement = view.findViewById(R.id.BudgetImportStatementButton);
        Button retry = view.findViewById(R.id.BudgetRetryButton);
        LinearLayout transactionList = view.findViewById(R.id.BudgetTransactionList);
        ProgressBar loading = view.findViewById(R.id.BudgetLoading);
        TextView monthLabel = view.findViewById(R.id.BudgetMonthLabel);
        ImageButton monthPrev = view.findViewById(R.id.BudgetMonthPrevButton);
        ImageButton monthNext = view.findViewById(R.id.BudgetMonthNextButton);
        LinearLayout limitBarsContainer = view.findViewById(R.id.BudgetLimitBarsContainer);
        Button setLimitButton = view.findViewById(R.id.BudgetSetLimitButton);
        Spinner accountSpinner = view.findViewById(R.id.BudgetAccountSpinner);
        RadioGroup rangeGroup = view.findViewById(R.id.BudgetRangeGroup);
        BudgetBalanceChartView chartView = view.findViewById(R.id.BudgetBalanceChart);

        budgetViewModel.getTitle().observe(getViewLifecycleOwner(), title::setText);
        budgetViewModel.getStatusMessage().observe(getViewLifecycleOwner(), status::setText);

        budgetViewModel.getSummaryData().observe(getViewLifecycleOwner(), data -> {
            if (data == null) return;
            summaryIncome.setText(String.format(Locale.GERMAN, "+%.2f €", data.getIncomeCents() / 100.0));
            summaryIncome.setTextColor(Color.parseColor("#4CAF50"));
            summaryExpense.setText(String.format(Locale.GERMAN, "-%.2f €", data.getExpenseCents() / 100.0));
            summaryExpense.setTextColor(Color.parseColor("#F44336"));
            long net = data.getNetCents();
            String sign = net >= 0 ? "+" : "-";
            summaryNet.setText(String.format(Locale.GERMAN, "%s%.2f €", sign, Math.abs(net) / 100.0));
            summaryNet.setTextColor(Color.parseColor(net >= 0 ? "#4CAF50" : "#F44336"));
        });

        budgetViewModel.getTransactions().observe(getViewLifecycleOwner(),
                rows -> renderTransactions(rows, transactionList));

        budgetViewModel.getChartPoints().observe(getViewLifecycleOwner(), chartView::setPoints);

        budgetViewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            boolean isLoading = state == BudgetViewModel.BudgetUiState.LOADING;
            boolean isError = state == BudgetViewModel.BudgetUiState.ERROR;
            boolean isContent = state == BudgetViewModel.BudgetUiState.CONTENT;
            loading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            retry.setVisibility(isError ? View.VISIBLE : View.GONE);
            transactionList.setVisibility(isContent ? View.VISIBLE : View.GONE);
            summaryCard.setVisibility(isContent ? View.VISIBLE : View.GONE);
        });

        budgetViewModel.getCurrentMonth().observe(getViewLifecycleOwner(), month ->
                monthLabel.setText(budgetViewModel.formatMonth(month)));

        budgetViewModel.getLimits().observe(getViewLifecycleOwner(),
                bars -> renderLimitBars(bars, limitBarsContainer, setLimitButton));

        budgetViewModel.getAccounts().observe(getViewLifecycleOwner(), accounts ->
                renderAccountSpinner(accounts, accountSpinner));

        budgetViewModel.getSelectedAccountId().observe(getViewLifecycleOwner(), selectedId -> {
            if (selectedId == null || accountItems.isEmpty()) return;
            for (int i = 0; i < accountItems.size(); i++) {
                if (selectedId.equals(accountItems.get(i).id) && accountSpinner.getSelectedItemPosition() != i) {
                    accountSpinner.setSelection(i, false);
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
            if (rangeGroup.getCheckedRadioButtonId() != checkedId) {
                rangeGroup.check(checkedId);
            }
        });

        budgetViewModel.getImportResult().observe(getViewLifecycleOwner(), result -> {
            if (result != null && !result.recurringSuggestions().isEmpty()) {
                showRecurringSuggestionsDialog(result.recurringSuggestions());
                budgetViewModel.clearImportResult();
            }
        });

        accountSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
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

        rangeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.BudgetRange30d) {
                budgetViewModel.setTimeRangeFilter(BudgetViewModel.TimeRangeFilter.DAYS_30);
            } else if (checkedId == R.id.BudgetRange3m) {
                budgetViewModel.setTimeRangeFilter(BudgetViewModel.TimeRangeFilter.MONTHS_3);
            } else if (checkedId == R.id.BudgetRange12m) {
                budgetViewModel.setTimeRangeFilter(BudgetViewModel.TimeRangeFilter.MONTHS_12);
            }
        });

        monthPrev.setOnClickListener(v -> budgetViewModel.navigateMonth(-1));
        monthNext.setOnClickListener(v -> budgetViewModel.navigateMonth(1));
        addTransaction.setOnClickListener(v -> showAddTransactionDialog());
        addTransfer.setOnClickListener(v -> showTransferDialog());
        importStatement.setOnClickListener(v ->
                csvPickerLauncher.launch(new String[]{"text/csv", "text/plain", "application/pdf", "*/*"}));
        retry.setOnClickListener(v -> budgetViewModel.retry());
        setLimitButton.setOnClickListener(v -> showEditLimitDialog(null, null, 0));
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

    // --- Add Transaction Dialog (with category picker) ---

    private void showAddTransactionDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.budget_add_transaction_dialog, null);
        TextInputEditText amountInput = dialogView.findViewById(R.id.BudgetDialogAmount);
        RadioButton expenseRadio = dialogView.findViewById(R.id.BudgetDialogTypeExpense);
        RadioGroup typeGroup = dialogView.findViewById(R.id.BudgetDialogTypeGroup);
        Spinner categorySpinner = dialogView.findViewById(R.id.BudgetDialogCategory);
        TextInputEditText noteInput = dialogView.findViewById(R.id.BudgetDialogNote);

        List<BudgetCategory> allCategories = budgetViewModel.getCategories().getValue();
        if (allCategories == null) allCategories = new ArrayList<>();
        List<BudgetCategory> allCats = allCategories;

        populateCategorySpinner(categorySpinner, allCats, true);

        typeGroup.setOnCheckedChangeListener((group, checkedId) ->
                populateCategorySpinner(categorySpinner, allCats,
                        checkedId == R.id.BudgetDialogTypeExpense));

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.budget_dialog_title)
                .setView(dialogView)
                .setPositiveButton(R.string.budget_dialog_save, (dialog, which) -> {
                    String amountStr = amountInput.getText() != null
                            ? amountInput.getText().toString().trim() : "";
                    boolean isExpense = expenseRadio.isChecked();
                    String note = noteInput.getText() != null
                            ? noteInput.getText().toString().trim() : "";
                    String categoryId = getSelectedCategoryId(categorySpinner, allCats, isExpense);

                    budgetViewModel.addTransaction(amountStr, isExpense, categoryId,
                            note.isEmpty() ? null : note, LocalDate.now());
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
                names.add(cat.name);
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
                    bar.getSpentCents() / 100.0, bar.getLimitEuros()));
            int pct = bar.getPercentage();
            percentText.setText(String.format(Locale.GERMAN, "%d%%", pct));
            progress.setProgress(Math.min(pct, 100));

            int color;
            if (pct > 100) {
                color = Color.parseColor("#F44336");
            } else if (pct >= 80) {
                color = Color.parseColor("#FF9800");
            } else {
                color = Color.parseColor("#4CAF50");
            }
            progress.setProgressTintList(ColorStateList.valueOf(color));
            percentText.setTextColor(color);

            row.setOnClickListener(v ->
                    showEditLimitDialog(bar.getCategoryId(), bar.getCategoryName(), bar.getLimitEuros()));
            container.addView(row);
        }
    }

    private void renderTransactions(List<BudgetViewModel.BudgetTransactionRow> rows,
                                    LinearLayout container) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(container.getContext());

        for (BudgetViewModel.BudgetTransactionRow row : rows) {
            View rowView = inflater.inflate(R.layout.budget_transaction_item, container, false);
            TextView label = rowView.findViewById(R.id.BudgetTransactionLabel);
            TextView amount = rowView.findViewById(R.id.BudgetTransactionAmount);
            label.setText(row.getLabel());
            amount.setText(row.getAmount());
            rowView.setContentDescription(
                    getString(R.string.budget_transaction_content_description,
                            row.getLabel(), row.getAmount()));

            rowView.setOnLongClickListener(v -> {
                showDeleteTransactionDialog(row);
                return true;
            });

            container.addView(rowView);
        }
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
                    ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));

            if (suggestion.confidenceScore() >= 0.7) {
                confidence.setTextColor(Color.parseColor("#4CAF50"));
            } else if (suggestion.confidenceScore() >= 0.5) {
                confidence.setTextColor(Color.parseColor("#FF9800"));
            } else {
                confidence.setTextColor(Color.parseColor("#9E9E9E"));
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

                    budgetViewModel.saveBudgetLimit(categoryId, amountEuros);
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
