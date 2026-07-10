package com.autosecretary.features.budget.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.autosecretary.features.budget.application.CalculateEffectiveBudgetLimitUseCase;
import com.autosecretary.features.budget.application.CreateTransferUseCase;
import com.autosecretary.features.budget.application.LoadBudgetOverviewUseCase;
import com.autosecretary.features.budget.domain.AmountParser;
import com.autosecretary.features.budget.application.BudgetSeedService;
import com.autosecretary.features.budget.application.importing.ApplyRecurringSuggestionsUseCase;
import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.domain.BudgetAccount;
import com.autosecretary.features.budget.domain.BudgetCategory;
import com.autosecretary.features.budget.domain.BudgetLimit;
import com.autosecretary.features.budget.domain.CategorySpendSummary;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.recurring.RecurringSuggestion;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.ui.internal.BudgetSummaryPresentationMapper;
import com.autosecretary.features.budget.ui.state.BudgetChartPoint;
import com.autosecretary.features.budget.ui.state.BudgetUiState;
import com.autosecretary.features.budget.ui.state.TimeRangeFilter;
import com.autosecretary.features.budget.ui.state.BudgetLimitBar;
import com.autosecretary.features.budget.ui.state.BudgetSummaryData;
import com.autosecretary.features.budget.ui.state.BudgetTransactionRow;
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
 * <p><strong>Threading:</strong> all DB operations are dispatched on {@code executor}
 * (single-threaded background). Results are posted back to the main thread via
 * {@code LiveData.postValue()}, consistent with {@code TaskViewModel}.
 */
public class BudgetViewModel extends ViewModel {

    public record Infrastructure(
            BudgetRepository repository,
            ExecutorService executor
    ) {
        public Infrastructure {
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(executor, "executor");
        }
    }

    public record UseCases(
            BudgetImportUseCase importUseCase,
            ApplyRecurringSuggestionsUseCase applyRecurringUseCase,
            CreateTransferUseCase createTransferUseCase,
            BudgetSeedService budgetSeedService
    ) {
        public UseCases {
            Objects.requireNonNull(importUseCase, "importUseCase");
            Objects.requireNonNull(applyRecurringUseCase, "applyRecurringUseCase");
            Objects.requireNonNull(createTransferUseCase, "createTransferUseCase");
            Objects.requireNonNull(budgetSeedService, "budgetSeedService");
        }
    }

    public record Presentation(
            CalculateEffectiveBudgetLimitUseCase calculateEffectiveLimitUseCase,
            LoadBudgetOverviewUseCase loadBudgetOverviewUseCase
    ) {
        public Presentation {
            Objects.requireNonNull(calculateEffectiveLimitUseCase, "calculateEffectiveLimitUseCase");
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

    private final BudgetRepository repository;
    private final ExecutorService executor;
    private final BudgetImportUseCase importUseCase;
    private final ApplyRecurringSuggestionsUseCase applyRecurringUseCase;
    private final CreateTransferUseCase createTransferUseCase;
    private final CalculateEffectiveBudgetLimitUseCase calculateEffectiveLimitUseCase;
    private final BudgetSeedService budgetSeedService;
    private final LoadBudgetOverviewUseCase loadBudgetOverviewUseCase;

    public BudgetViewModel(Infrastructure infrastructure,
                           UseCases useCases,
                           Presentation presentation) {
        this.repository = infrastructure.repository();
        this.executor = infrastructure.executor();
        this.importUseCase = useCases.importUseCase();
        this.applyRecurringUseCase = useCases.applyRecurringUseCase();
        this.createTransferUseCase = useCases.createTransferUseCase();
        this.calculateEffectiveLimitUseCase = presentation.calculateEffectiveLimitUseCase();
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
        executor.execute(() -> {
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
        executor.execute(this::loadOverviewOnExecutor);
    }

    // Called directly when already running on the executor, to avoid re-queuing via loadOverview().
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
            repository.saveTransaction(new BudgetRepository.TransactionCreateDetails(
                    accountId, categoryId,
                    isExpense ? TransactionDirection.EXPENSE : TransactionDirection.INCOME,
                    amountCents, date, note));
            loadOverviewOnExecutor();
        });
    }

    public void updateTransaction(String transactionId, String amountStr, boolean isExpense,
                                  String categoryId, String note, LocalDate date, String accountId) {
        withParsedAmount(amountStr, amountCents -> {
            repository.updateTransaction(transactionId, new BudgetRepository.TransactionCreateDetails(
                    accountId, categoryId,
                    isExpense ? TransactionDirection.EXPENSE : TransactionDirection.INCOME,
                    amountCents, date, note));
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
        executor.execute(() -> {
            repository.deleteTransaction(transactionId);
            loadOverviewOnExecutor();
        });
    }

    public void importFromCsv(String fileName, byte[] bytes, String mimeType) {
        uiState.postValue(BudgetUiState.LOADING);

        executor.execute(() -> {
            String accountId = resolveAccountId();
            if (accountId == null) {
                uiState.postValue(BudgetUiState.ERROR);
                statusMessage.postValue(UiText.of(R.string.budget_status_no_account));
                return;
            }

            try {
                BudgetImportUseCase.ImportResult result =
                        importUseCase.execute(accountId, fileName, bytes, mimeType);
                loadOverviewOnExecutor();
                statusMessage.postValue(UiText.of(R.string.budget_status_import_success,
                        result.newTransactions(), result.duplicates(), result.recognizedCategories()));
                importSuggestions.postValue(result.recurringSuggestions());
            } catch (RuntimeException e) {
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                uiState.postValue(BudgetUiState.ERROR);
                statusMessage.postValue(UiText.of(R.string.budget_status_import_failed, errorMessage));
            }
        });
    }

    public void onImportReadFailed() {
        uiState.postValue(BudgetUiState.ERROR);
        statusMessage.postValue(UiText.of(R.string.budget_status_file_read_failed));
    }

    public void setImportStatus(String message) {
        statusMessage.postValue(UiText.raw(message));
    }

    public void applyRecurringSuggestions(List<RecurringSuggestion> suggestions) {
        executor.execute(() -> {
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

    // Parses the amount string and invokes action on the executor thread (background).
    // Callers must not perform any UI or main-thread operations inside the action lambda —
    // use postValue() / uiState.postValue() to post results back to the main thread.
    private void withParsedAmount(String amountStr, LongConsumer action) {
        executor.execute(() -> {
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
    // Must be called on the executor thread — performs a synchronous DB read.
    private String resolveAccountId() {
        List<BudgetAccount> accounts = repository.findActiveAccounts();
        String current = selectedAccountId.getValue();
        if (current != null && !current.isBlank()) return current;
        return accounts.isEmpty() ? null : accounts.get(0).id();
    }

    private void loadLimitsOnExecutor(YearMonth month) {
        String yearMonthStr = month.toString();

        List<CategorySpendSummary> totals = repository.getCategorySpendTotals(yearMonthStr);
        List<BudgetLimitBar> bars = BudgetSummaryPresentationMapper.toLimitBars(
                totals,
                (catId, ym) -> calculateEffectiveLimitUseCase.execute(catId, ym).effectiveLimitCents(),
                yearMonthStr
        );
        budgetLimits.postValue(bars);
    }

    public void saveBudgetLimitFromString(String categoryId, String amountStr,
                                          boolean rolloverEnabled, String rolloverCarryoverStr) {
        executor.execute(() -> {
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
            repository.saveBudgetLimit(limit);
            loadOverviewOnExecutor();
        });
    }
}
