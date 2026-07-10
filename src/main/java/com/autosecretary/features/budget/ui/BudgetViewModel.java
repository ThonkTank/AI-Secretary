package com.autosecretary.features.budget.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.autosecretary.features.budget.application.BudgetTransactionMutationUseCase;
import com.autosecretary.features.budget.application.CreateTransferUseCase;
import com.autosecretary.features.budget.application.LoadBudgetLimitOverviewUseCase;
import com.autosecretary.features.budget.application.LoadBudgetOverviewUseCase;
import com.autosecretary.features.budget.application.ResolveBudgetAccountUseCase;
import com.autosecretary.features.budget.domain.AmountParser;
import com.autosecretary.features.budget.application.BudgetSeedService;
import com.autosecretary.features.budget.application.importing.ApplyRecurringSuggestionsUseCase;
import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.domain.BudgetAccount;
import com.autosecretary.features.budget.domain.BudgetCategory;
import com.autosecretary.features.budget.domain.BudgetLimit;
import com.autosecretary.features.budget.domain.importing.ParsedStatement;
import com.autosecretary.features.budget.domain.recurring.RecurringSuggestion;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.application.overview.BudgetChartPoint;
import com.autosecretary.features.budget.ui.state.BudgetUiState;
import com.autosecretary.features.budget.application.overview.TimeRangeFilter;
import com.autosecretary.features.budget.ui.state.BudgetLimitBar;
import com.autosecretary.features.budget.application.overview.BudgetSummaryData;
import com.autosecretary.features.budget.application.overview.BudgetTransactionRow;
import com.autosecretary.features.budget.ui.internal.BudgetLimitPresentationMapper;
import com.autosecretary.features.budget.ui.state.UiText;

import com.autosecretary.R;

import com.autosecretary.shared.DateFormatters;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.LongConsumer;

/**
 * Owns all observable state for the budget screen. Observed by {@link BudgetFragment}.
 *
 * <p><strong>Responsibilities:</strong>
 * <ul>
 *   <li>Account and time-range selection (drives which data is loaded)</li>
 *   <li>Loading summary + transaction + chart data via {@link LoadBudgetOverviewUseCase}</li>
 *   <li>Delegating import, transfer, and seed operations to their respective use cases</li>
 *   <li>Exposing all display state as {@link androidx.lifecycle.LiveData} streams</li>
 * </ul>
 *
 * <p><strong>LiveData streams (observed by BudgetFragment):</strong>
 * <ul>
 *   <li>{@code summaryData} — monthly income / expense / net / running balance</li>
 *   <li>{@code transactions} — flat list of transaction rows for the selected account + month</li>
 *   <li>{@code budgetLimits} — category spending-limit progress bars</li>
 *   <li>{@code chartPoints} — balance timeline points for the chart view</li>
 *   <li>{@code uiState} — LOADING / EMPTY / CONTENT / ERROR (controls view visibility)</li>
 *   <li>{@code statusMessage} — transient status / error text shown in a banner</li>
 *   <li>{@code accounts} / {@code categories} — reference data for spinners and dialogs</li>
 *   <li>{@code importSuggestions} — recurring suggestions returned after an import</li>
 * </ul>
 *
 * <p><strong>Threading:</strong> DB operations are dispatched on {@code dbExecutor}
 * (single-threaded background). Import parsing runs on {@code ioExecutor}. Results are
 * posted back to the main thread via {@code LiveData.postValue()}, consistent with
 * {@code TaskViewModel}.
 */
public class BudgetViewModel extends ViewModel {

    public record Infrastructure(
            ExecutorService dbExecutor,
            ExecutorService ioExecutor
    ) {
        public Infrastructure {
            Objects.requireNonNull(dbExecutor, "dbExecutor");
            Objects.requireNonNull(ioExecutor, "ioExecutor");
        }
    }

    public record UseCases(
            BudgetImportUseCase importUseCase,
            ApplyRecurringSuggestionsUseCase applyRecurringUseCase,
            CreateTransferUseCase createTransferUseCase,
            BudgetTransactionMutationUseCase transactionMutationUseCase,
            ResolveBudgetAccountUseCase resolveBudgetAccountUseCase,
            LoadBudgetLimitOverviewUseCase loadBudgetLimitOverviewUseCase,
            BudgetSeedService budgetSeedService
    ) {
        public UseCases {
            Objects.requireNonNull(importUseCase, "importUseCase");
            Objects.requireNonNull(applyRecurringUseCase, "applyRecurringUseCase");
            Objects.requireNonNull(createTransferUseCase, "createTransferUseCase");
            Objects.requireNonNull(transactionMutationUseCase, "transactionMutationUseCase");
            Objects.requireNonNull(resolveBudgetAccountUseCase, "resolveBudgetAccountUseCase");
            Objects.requireNonNull(loadBudgetLimitOverviewUseCase, "loadBudgetLimitOverviewUseCase");
            Objects.requireNonNull(budgetSeedService, "budgetSeedService");
        }
    }

    public record Presentation(
            LoadBudgetOverviewUseCase loadBudgetOverviewUseCase
    ) {
        public Presentation {
            Objects.requireNonNull(loadBudgetOverviewUseCase, "loadBudgetOverviewUseCase");
        }
    }

    private final MutableLiveData<BudgetSummaryData> summaryData = new MutableLiveData<>();
    private final MutableLiveData<BudgetUiState> uiState = new MutableLiveData<>(BudgetUiState.LOADING);
    private final MutableLiveData<UiText> statusMessage = new MutableLiveData<>(UiText.raw(""));
    private final MutableLiveData<List<BudgetTransactionRow>> transactions = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<RecurringSuggestion>> importSuggestions = new MutableLiveData<>();
    private final MutableLiveData<YearMonth> currentMonth = new MutableLiveData<>(YearMonth.now());
    private final MutableLiveData<List<BudgetCategory>> categories = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<BudgetAccount>> accounts = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> selectedAccountId = new MutableLiveData<>();
    private final MutableLiveData<TimeRangeFilter> timeRangeFilter = new MutableLiveData<>(TimeRangeFilter.DAYS_30);
    private final MutableLiveData<List<BudgetChartPoint>> chartPoints = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<BudgetLimitBar>> budgetLimits = new MutableLiveData<>(new ArrayList<>());

    private final ExecutorService dbExecutor;
    private final ExecutorService ioExecutor;
    private final BudgetImportUseCase importUseCase;
    private final ApplyRecurringSuggestionsUseCase applyRecurringUseCase;
    private final CreateTransferUseCase createTransferUseCase;
    private final BudgetTransactionMutationUseCase transactionMutationUseCase;
    private final ResolveBudgetAccountUseCase resolveBudgetAccountUseCase;
    private final LoadBudgetLimitOverviewUseCase loadBudgetLimitOverviewUseCase;
    private final BudgetSeedService budgetSeedService;
    private final LoadBudgetOverviewUseCase loadBudgetOverviewUseCase;

    public BudgetViewModel(Infrastructure infrastructure,
                           UseCases useCases,
                           Presentation presentation) {
        this.dbExecutor = infrastructure.dbExecutor();
        this.ioExecutor = infrastructure.ioExecutor();
        this.importUseCase = useCases.importUseCase();
        this.applyRecurringUseCase = useCases.applyRecurringUseCase();
        this.createTransferUseCase = useCases.createTransferUseCase();
        this.transactionMutationUseCase = useCases.transactionMutationUseCase();
        this.resolveBudgetAccountUseCase = useCases.resolveBudgetAccountUseCase();
        this.loadBudgetLimitOverviewUseCase = useCases.loadBudgetLimitOverviewUseCase();
        this.budgetSeedService = useCases.budgetSeedService();
        this.loadBudgetOverviewUseCase = presentation.loadBudgetOverviewUseCase();
        ensureDefaultData();
    }

    public LiveData<BudgetSummaryData> getSummaryData() { return summaryData; }
    public LiveData<BudgetUiState> getUiState() { return uiState; }
    public LiveData<UiText> getStatusMessage() { return statusMessage; }
    public LiveData<List<BudgetTransactionRow>> getTransactions() { return transactions; }
    public LiveData<List<RecurringSuggestion>> getImportSuggestions() { return importSuggestions; }
    public LiveData<YearMonth> getCurrentMonth() { return currentMonth; }
    public LiveData<List<BudgetCategory>> getCategories() { return categories; }
    public LiveData<List<BudgetAccount>> getAccounts() { return accounts; }
    public LiveData<String> getSelectedAccountId() { return selectedAccountId; }
    public LiveData<TimeRangeFilter> getTimeRangeFilter() { return timeRangeFilter; }
    public LiveData<List<BudgetChartPoint>> getChartPoints() { return chartPoints; }
    public LiveData<List<BudgetLimitBar>> getLimits() { return budgetLimits; }

    public void setSelectedAccount(String accountId) {
        if (accountId == null || accountId.equals(selectedAccountId.getValue())) {
            return;
        }
        selectedAccountId.setValue(accountId);
        loadOverview();
    }

    public void setTimeRangeFilter(TimeRangeFilter filter) {
        if (filter == null || filter == timeRangeFilter.getValue()) {
            return;
        }
        timeRangeFilter.setValue(filter);
        loadOverview();
    }

    public void clearImportResult() {
        importSuggestions.setValue(null);
    }

    // Seeds default account + categories on first launch (no-op if data already exists), then loads the overview.
    private void ensureDefaultData() {
        dbExecutor.execute(() -> {
            BudgetSeedService.SeedResult seedResult = budgetSeedService.ensureDefaultData(selectedAccountId.getValue());
            categories.postValue(seedResult.categories());
            accounts.postValue(seedResult.accounts());
            if (selectedAccountId.getValue() == null && seedResult.selectedAccountId() != null) {
                selectedAccountId.postValue(seedResult.selectedAccountId());
            }
            loadOverviewOnExecutor();
        });
    }


    public void navigateMonth(int offset) {
        YearMonth current = currentMonth.getValue();
        if (current == null) current = YearMonth.now();
        currentMonth.setValue(current.plusMonths(offset));
        loadOverview();
    }

    public String formatMonth(YearMonth month) {
        return month.format(DateFormatters.MONTH_LABEL);
    }

    public void loadOverview() {
        uiState.postValue(BudgetUiState.LOADING);
        dbExecutor.execute(this::loadOverviewOnExecutor);
    }

    // Called directly when already running on the DB executor, to avoid re-queuing via loadOverview().
    private void loadOverviewOnExecutor() {
        YearMonth month = currentMonth.getValue();
        if (month == null) month = YearMonth.now();

        LoadBudgetOverviewUseCase.OverviewData overview = loadBudgetOverviewUseCase.load(
                month,
                selectedAccountId.getValue(),
                timeRangeFilter.getValue());
        if (overview.accountId() == null) {
            uiState.postValue(BudgetUiState.EMPTY);
            statusMessage.postValue(UiText.of(R.string.budget_status_no_account));
            accounts.postValue(new ArrayList<>());
            chartPoints.postValue(new ArrayList<>());
            return;
        }

        accounts.postValue(overview.accounts());
        selectedAccountId.postValue(overview.accountId());

        publishOverviewState(overview.rows(), overview.chartPoints(), overview.summary());
        loadLimitsOnExecutor(month);
    }

    private void publishOverviewState(List<BudgetTransactionRow> rows,
                                      List<BudgetChartPoint> balancePoints,
                                      BudgetSummaryData summary) {
        transactions.postValue(rows);
        chartPoints.postValue(balancePoints);
        summaryData.postValue(summary);
        if (!rows.isEmpty()) {
            uiState.postValue(BudgetUiState.CONTENT);
            statusMessage.postValue(UiText.of(R.string.budget_status_last_bookings));
        } else {
            uiState.postValue(BudgetUiState.EMPTY);
            statusMessage.postValue(UiText.of(R.string.budget_status_no_bookings));
        }
    }

    public void addTransaction(String amountStr, boolean isExpense, String categoryId,
                               String note, LocalDate date, String accountId) {
        withParsedAmount(amountStr, amountCents -> {
            if (accountId == null) return;
            transactionMutationUseCase.create(
                    accountId,
                    categoryId,
                    isExpense ? TransactionDirection.EXPENSE : TransactionDirection.INCOME,
                    amountCents,
                    date,
                    note);
            loadOverviewOnExecutor();
        });
    }

    public void updateTransaction(String transactionId, String amountStr, boolean isExpense,
                                  String categoryId, String note, LocalDate date, String accountId) {
        withParsedAmount(amountStr, amountCents -> {
            transactionMutationUseCase.update(
                    transactionId,
                    accountId,
                    categoryId,
                    isExpense ? TransactionDirection.EXPENSE : TransactionDirection.INCOME,
                    amountCents,
                    date,
                    note);
            loadOverviewOnExecutor();
        });
    }

    public void addTransfer(String sourceAccountId,
                            String targetAccountId,
                            String amountStr,
                            LocalDate date,
                            String note) {
        withParsedAmount(amountStr, amountCents -> {
            CreateTransferUseCase.Result result = createTransferUseCase.execute(
                    sourceAccountId, targetAccountId, amountCents, date, note);
            if (!result.success()) {
                postTransferError(result);
                return;
            }
            loadOverviewOnExecutor();
        });
    }

    private void postTransferError(CreateTransferUseCase.Result result) {
        uiState.postValue(BudgetUiState.ERROR);
        statusMessage.postValue(UiText.raw(result.errorMessage()));
    }

    public void deleteTransaction(String transactionId) {
        dbExecutor.execute(() -> {
            transactionMutationUseCase.delete(transactionId);
            loadOverviewOnExecutor();
        });
    }

    public void importFromCsv(String fileName, byte[] bytes, String mimeType) {
        uiState.postValue(BudgetUiState.LOADING);

        dbExecutor.execute(() -> {
            String accountId = resolveAccountId();
            if (accountId == null) {
                uiState.postValue(BudgetUiState.ERROR);
                statusMessage.postValue(UiText.of(R.string.budget_status_no_account));
                return;
            }

            try {
                BudgetImportUseCase.ImportContext importContext = importUseCase.beginImport(
                        accountId,
                        fileName,
                        bytes,
                        shouldLoadImportCategories(fileName, mimeType));
                parseImportOnIo(importContext, bytes, mimeType);
            } catch (RuntimeException e) {
                postImportError(BudgetImportUseCase.userErrorMessage(e));
            }
        });
    }

    private void parseImportOnIo(BudgetImportUseCase.ImportContext importContext,
                                 byte[] bytes,
                                 String mimeType) {
        ioExecutor.execute(() -> {
            try {
                ParsedStatement parsed = importUseCase.parse(importContext, bytes, mimeType);
                completeImportOnDb(importContext, parsed);
            } catch (RuntimeException e) {
                markImportFailedOnDb(importContext, BudgetImportUseCase.userErrorMessage(e));
            }
        });
    }

    private void completeImportOnDb(BudgetImportUseCase.ImportContext importContext,
                                    ParsedStatement parsed) {
        dbExecutor.execute(() -> {
            try {
                BudgetImportUseCase.ImportResult result = importUseCase.completeImport(importContext, parsed);
                loadOverviewOnExecutor();
                statusMessage.postValue(UiText.of(R.string.budget_status_import_success,
                        result.newTransactions(), result.duplicates(), result.recognizedCategories()));
                importSuggestions.postValue(result.recurringSuggestions());
            } catch (RuntimeException e) {
                markImportFailedOnCurrentDbThread(importContext, BudgetImportUseCase.userErrorMessage(e));
            }
        });
    }

    private void markImportFailedOnDb(BudgetImportUseCase.ImportContext importContext,
                                      String errorMessage) {
        dbExecutor.execute(() -> markImportFailedOnCurrentDbThread(importContext, errorMessage));
    }

    private void markImportFailedOnCurrentDbThread(BudgetImportUseCase.ImportContext importContext,
                                                   String errorMessage) {
        try {
            importUseCase.markImportFailed(importContext.importId(), errorMessage);
        } catch (RuntimeException ignored) {
            // The user-visible failure should not be replaced by a secondary status-update error.
        }
        postImportError(errorMessage);
    }

    private void postImportError(String errorMessage) {
        uiState.postValue(BudgetUiState.ERROR);
        statusMessage.postValue(UiText.of(R.string.budget_status_import_failed, errorMessage));
    }

    private boolean shouldLoadImportCategories(String fileName, String mimeType) {
        String normalizedMimeType = mimeType != null ? mimeType.toLowerCase() : "";
        String normalizedFileName = fileName != null ? fileName.toLowerCase() : "";
        return normalizedMimeType.contains("pdf") || normalizedFileName.endsWith(".pdf");
    }

    public void onImportReadFailed() {
        uiState.postValue(BudgetUiState.ERROR);
        statusMessage.postValue(UiText.of(R.string.budget_status_file_read_failed));
    }

    public void setImportStatus(String message) {
        statusMessage.postValue(UiText.raw(message));
    }

    public void applyRecurringSuggestions(List<RecurringSuggestion> suggestions) {
        dbExecutor.execute(() -> {
            String accountId = resolveAccountId();
            if (accountId == null) return;

            try {
                applyRecurringUseCase.execute(accountId, suggestions);
                loadOverviewOnExecutor();
            } catch (RuntimeException e) {
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                statusMessage.postValue(UiText.of(R.string.budget_status_error, errorMessage));
            }
        });
    }

    public void retry() {
        loadOverview();
    }

    // Parses the amount string and invokes action on the DB executor thread (background).
    // Callers must not perform any UI or main-thread operations inside the action lambda —
    // use postValue() / uiState.postValue() to post results back to the main thread.
    private void withParsedAmount(String amountStr, LongConsumer action) {
        dbExecutor.execute(() -> {
            Long amountCents = AmountParser.parseAmountCents(amountStr);
            if (amountCents == null) {
                showInvalidAmountError();
                return;
            }
            action.accept(amountCents);
        });
    }

    private void showInvalidAmountError() {
        uiState.postValue(BudgetUiState.ERROR);
        statusMessage.postValue(UiText.of(R.string.budget_status_invalid_amount));
    }

    // Returns the selected account ID, falling back to the first active account.
    // Null only when no accounts exist yet.
    // Must be called on the DB executor thread — performs a synchronous DB read.
    private String resolveAccountId() {
        return resolveBudgetAccountUseCase.execute(selectedAccountId.getValue());
    }

    private void loadLimitsOnExecutor(YearMonth month) {
        String yearMonthStr = month.toString();
        budgetLimits.postValue(BudgetLimitPresentationMapper.toLimitBars(
                loadBudgetLimitOverviewUseCase.execute(yearMonthStr)));
    }

    public void saveBudgetLimitFromString(String categoryId, String amountStr,
                                          boolean rolloverEnabled, String rolloverCarryoverStr) {
        dbExecutor.execute(() -> {
            Long amountCents = AmountParser.parseAmountCents(amountStr);
            if (amountCents == null) {
                showInvalidAmountError();
                return;
            }
            long rolloverCarryoverCents = 0L;
            if (rolloverCarryoverStr != null && !rolloverCarryoverStr.isEmpty()) {
                Long parsed = AmountParser.parseAmountCents(rolloverCarryoverStr);
                if (parsed == null) {
                    showInvalidAmountError();
                    return;
                }
                rolloverCarryoverCents = parsed;
            }
            YearMonth month = currentMonth.getValue();
            if (month == null) month = YearMonth.now();
            String yearMonthStr = month.toString();
            BudgetLimit limit = BudgetLimit.create(
                    categoryId, yearMonthStr, amountCents, rolloverEnabled, rolloverCarryoverCents);
            loadBudgetLimitOverviewUseCase.saveBudgetLimit(limit);
            loadOverviewOnExecutor();
        });
    }
}
