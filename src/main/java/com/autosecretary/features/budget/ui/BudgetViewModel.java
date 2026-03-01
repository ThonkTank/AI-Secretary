package com.autosecretary.features.budget.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.autosecretary.features.budget.application.CalculateEffectiveBudgetLimitUseCase;
import com.autosecretary.features.budget.application.CreateTransferUseCase;
import com.autosecretary.features.budget.domain.AmountParser;
import com.autosecretary.features.budget.application.BudgetSeedService;
import com.autosecretary.features.budget.application.importing.ApplyRecurringSuggestionsUseCase;
import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.data.entity.BudgetLimit;
import com.autosecretary.features.budget.domain.CategorySpendSummary;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.recurring.RecurringSuggestion;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.ui.internal.BudgetOverviewLoader;
import com.autosecretary.features.budget.ui.internal.BudgetSummaryPresentationMapper;
import com.autosecretary.features.budget.ui.state.BudgetChartPoint;
import com.autosecretary.features.budget.ui.state.BudgetUiState;
import com.autosecretary.features.budget.ui.state.TimeRangeFilter;
import com.autosecretary.features.budget.ui.state.BudgetLimitBar;
import com.autosecretary.features.budget.ui.state.BudgetSummaryData;
import com.autosecretary.features.budget.ui.state.BudgetTransactionRow;
import com.autosecretary.features.budget.ui.state.UiText;

import com.autosecretary.R;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.function.LongConsumer;

/**
 * Owns all observable state for the budget screen. Observed by {@link BudgetFragment}.
 *
 * <p><strong>Responsibilities:</strong>
 * <ul>
 *   <li>Account and time-range selection (drives which data is loaded)</li>
 *   <li>Loading summary + transaction + chart + limit data via {@link BudgetOverviewLoader}</li>
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

    private static final DateTimeFormatter MONTH_FORMATTER =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN);

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
    private final BudgetSummaryPresentationMapper summaryPresentationMapper;
    private final BudgetSeedService budgetSeedService;
    private final BudgetOverviewLoader budgetOverviewLoader;

    public BudgetViewModel(BudgetRepository repository,
                           ExecutorService executor,
                           BudgetImportUseCase importUseCase,
                           ApplyRecurringSuggestionsUseCase applyRecurringUseCase,
                           CreateTransferUseCase createTransferUseCase,
                           CalculateEffectiveBudgetLimitUseCase calculateEffectiveLimitUseCase,
                           BudgetOverviewLoader budgetOverviewLoader,
                           BudgetSummaryPresentationMapper summaryPresentationMapper,
                           BudgetSeedService budgetSeedService) {
        this.repository = repository;
        this.executor = executor;
        this.importUseCase = importUseCase;
        this.applyRecurringUseCase = applyRecurringUseCase;
        this.createTransferUseCase = createTransferUseCase;
        this.calculateEffectiveLimitUseCase = calculateEffectiveLimitUseCase;
        this.summaryPresentationMapper = summaryPresentationMapper;
        this.budgetSeedService = budgetSeedService;
        this.budgetOverviewLoader = budgetOverviewLoader;
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
        return month.format(MONTH_FORMATTER);
    }

    public void loadOverview() {
        uiState.postValue(BudgetUiState.LOADING);
        executor.execute(this::loadOverviewOnExecutor);
    }

    // Called directly when already running on the executor, to avoid re-queuing via loadOverview().
    private void loadOverviewOnExecutor() {
        YearMonth month = currentMonth.getValue();
        if (month == null) month = YearMonth.now();

        BudgetOverviewLoader.OverviewData overview = budgetOverviewLoader.load(
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

    public void updateTransfer(String transactionId,
                               String sourceAccountId,
                               String targetAccountId,
                               String amountStr,
                               LocalDate date,
                               String note) {
        withParsedAmount(amountStr, amountCents -> {
            CreateTransferUseCase.Result result = createTransferUseCase.update(
                    transactionId, sourceAccountId, targetAccountId, amountCents, date, note);
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
            List<BudgetAccount> accountList = repository.findActiveAccounts();
            String accountId = BudgetOverviewLoader.resolveSelectedAccountId(selectedAccountId.getValue(), accountList);
            if (accountId == null) {
                uiState.postValue(BudgetUiState.ERROR);
                statusMessage.postValue(UiText.of(R.string.budget_status_no_account));
                return;
            }

            importUseCase.executeAsync(accountId, fileName, bytes, mimeType, new BudgetImportUseCase.ImportCallback() {
                @Override
                public void onSuccess(BudgetImportUseCase.ImportResult result) {
                    loadOverview();
                    statusMessage.postValue(UiText.of(R.string.budget_status_import_success,
                            result.newTransactions(), result.duplicates(), result.recognizedCategories()));
                    importSuggestions.postValue(result.recurringSuggestions());
                }

                @Override
                public void onError(String errorMessage) {
                    uiState.postValue(BudgetUiState.ERROR);
                    statusMessage.postValue(UiText.of(R.string.budget_status_import_failed, errorMessage));
                }
            });
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
            List<BudgetAccount> accountList = repository.findActiveAccounts();
            String accountId = BudgetOverviewLoader.resolveSelectedAccountId(selectedAccountId.getValue(), accountList);
            if (accountId == null) return;

            applyRecurringUseCase.executeAsync(
                    accountId,
                    suggestions,
                    new ApplyRecurringSuggestionsUseCase.ApplyCallback() {
                        @Override
                        public void onSuccess() {
                            loadOverview();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            statusMessage.postValue(UiText.of(R.string.budget_status_error, errorMessage));
                        }
                    }
            );
        });
    }

    public void retry() {
        loadOverview();
    }

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

    private void loadLimitsOnExecutor(YearMonth month) {
        String yearMonthStr = month.toString();

        List<CategorySpendSummary> totals = repository.getCategorySpendTotals(yearMonthStr);
        List<BudgetLimitBar> bars = summaryPresentationMapper.toLimitBars(
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
            BudgetLimit limit = new BudgetLimit(categoryId, yearMonthStr, amountCents);
            limit.rolloverEnabled = rolloverEnabled;
            limit.rolloverCarryoverCents = rolloverCarryoverCents;
            repository.saveBudgetLimit(limit);
            loadOverviewOnExecutor();
        });
    }
}
