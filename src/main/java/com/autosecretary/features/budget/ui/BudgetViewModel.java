package com.autosecretary.features.budget.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.autosecretary.features.budget.application.CreateTransferUseCase;
import com.autosecretary.features.budget.application.importing.ApplyRecurringSuggestionsUseCase;
import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.application.importing.StatementFileParser;
import com.autosecretary.features.budget.data.projection.AccountDailyDeltaPoint;
import com.autosecretary.features.budget.data.projection.AccountMonthlyDeltaPoint;
import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.data.entity.BudgetLimit;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.data.projection.CategorySpendTotal;
import com.autosecretary.features.budget.data.projection.MonthlyTransactionOverviewItem;
import com.autosecretary.features.budget.domain.AccountBalanceTimelineService;
import com.autosecretary.features.budget.domain.BalanceTimelinePoint;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.CalculateFreeBudgetUseCase;
import com.autosecretary.features.budget.domain.RecurringSuggestion;
import com.autosecretary.features.budget.ui.internal.BudgetChartStateMapper;
import com.autosecretary.features.budget.ui.internal.BudgetImportDialogStateMapper;
import com.autosecretary.features.budget.ui.internal.BudgetSummaryPresentationMapper;
import com.autosecretary.features.budget.ui.state.BudgetChartPoint;
import com.autosecretary.features.budget.ui.state.BudgetImportDialogState;
import com.autosecretary.features.budget.ui.state.BudgetLimitBar;
import com.autosecretary.features.budget.ui.state.BudgetSummaryData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class BudgetViewModel extends ViewModel {

    public enum BudgetUiState {
        LOADING,
        EMPTY,
        CONTENT,
        ERROR
    }

    public enum TimeRangeFilter {
        DAYS_30,
        MONTHS_3,
        MONTHS_12
    }

    public static class BudgetTransactionRow {
        private final String transactionId;
        private final String label;
        private final String amount;
        private final boolean isExpense;
        private final String categoryColorHex;
        private final long amountCents;
        private final BudgetTransactionEntity.TransactionType type;
        private final String categoryId;
        private final String note;
        private final LocalDate bookingDate;
        private final String accountId;

        public BudgetTransactionRow(String transactionId, String label, String amount, boolean isExpense,
                                    String categoryColorHex, long amountCents,
                                    BudgetTransactionEntity.TransactionType type, String categoryId,
                                    String note, LocalDate bookingDate, String accountId) {
            this.transactionId = transactionId;
            this.label = label;
            this.amount = amount;
            this.isExpense = isExpense;
            this.categoryColorHex = categoryColorHex;
            this.amountCents = amountCents;
            this.type = type;
            this.categoryId = categoryId;
            this.note = note;
            this.bookingDate = bookingDate;
            this.accountId = accountId;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public String getLabel() {
            return label;
        }

        public String getAmount() {
            return amount;
        }

        public boolean isExpense() {
            return isExpense;
        }

        public String getCategoryColorHex() {
            return categoryColorHex;
        }

        public long getAmountCents() {
            return amountCents;
        }

        public BudgetTransactionEntity.TransactionType getType() {
            return type;
        }

        public String getCategoryId() {
            return categoryId;
        }

        public String getNote() {
            return note;
        }

        public LocalDate getBookingDate() {
            return bookingDate;
        }

        public String getAccountId() {
            return accountId;
        }
    }

    private static final DateTimeFormatter MONTH_FORMATTER =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN);
    private static final DateTimeFormatter DAILY_POINT_LABEL =
            DateTimeFormatter.ofPattern("dd.MM", Locale.GERMAN);
    private static final DateTimeFormatter MONTHLY_POINT_LABEL =
            DateTimeFormatter.ofPattern("MMM yy", Locale.GERMAN);

    private final MutableLiveData<String> title = new MutableLiveData<>("Budgetübersicht");
    private final MutableLiveData<BudgetSummaryData> summaryData = new MutableLiveData<>();
    private final MutableLiveData<BudgetUiState> uiState = new MutableLiveData<>(BudgetUiState.LOADING);
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>("");
    private final MutableLiveData<List<BudgetTransactionRow>> transactions = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<BudgetImportDialogState> importDialogState = new MutableLiveData<>();
    private final MutableLiveData<YearMonth> currentMonth = new MutableLiveData<>(YearMonth.now());
    private final MutableLiveData<List<BudgetCategory>> categories = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<BudgetAccount>> accounts = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> selectedAccountId = new MutableLiveData<>();
    private final MutableLiveData<TimeRangeFilter> timeRangeFilter = new MutableLiveData<>(TimeRangeFilter.DAYS_30);
    private final MutableLiveData<List<BudgetChartPoint>> chartPoints = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<BudgetLimitBar>> budgetLimits = new MutableLiveData<>(new ArrayList<>());

    private final BudgetRepository repository;
    private final StatementFileParser parser;
    private final ExecutorService executor;
    private final Consumer<Runnable> postToMain;
    private final BudgetImportUseCase importUseCase;
    private final ApplyRecurringSuggestionsUseCase applyRecurringUseCase;
    private final CreateTransferUseCase createTransferUseCase;
    private final AccountBalanceTimelineService balanceTimelineService;
    private final CalculateFreeBudgetUseCase calculateFreeBudgetUseCase;
    private final BudgetChartStateMapper chartStateMapper;
    private final BudgetSummaryPresentationMapper summaryPresentationMapper;
    private final BudgetImportDialogStateMapper importDialogStateMapper;

    public BudgetViewModel(BudgetRepository repository,
                           StatementFileParser parser,
                           ExecutorService executor,
                           Consumer<Runnable> postToMain,
                           BudgetImportUseCase importUseCase,
                           ApplyRecurringSuggestionsUseCase applyRecurringUseCase,
                           CalculateFreeBudgetUseCase calculateFreeBudgetUseCase) {
        this.repository = repository;
        this.parser = parser;
        this.executor = executor;
        this.postToMain = postToMain;
        this.importUseCase = importUseCase;
        this.applyRecurringUseCase = applyRecurringUseCase;
        this.createTransferUseCase = new CreateTransferUseCase(repository);
        this.balanceTimelineService = new AccountBalanceTimelineService();
        this.calculateFreeBudgetUseCase = calculateFreeBudgetUseCase;
        this.chartStateMapper = new BudgetChartStateMapper(DAILY_POINT_LABEL, MONTHLY_POINT_LABEL);
        this.summaryPresentationMapper = new BudgetSummaryPresentationMapper();
        this.importDialogStateMapper = new BudgetImportDialogStateMapper();
        ensureDefaultData();
    }

    public LiveData<String> getTitle() { return title; }
    public LiveData<BudgetSummaryData> getSummaryData() { return summaryData; }
    public LiveData<BudgetUiState> getUiState() { return uiState; }
    public LiveData<String> getStatusMessage() { return statusMessage; }
    public LiveData<List<BudgetTransactionRow>> getTransactions() { return transactions; }
    public LiveData<BudgetImportDialogState> getImportDialogState() { return importDialogState; }
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
        importDialogState.setValue(null);
    }

    private void ensureDefaultData() {
        executor.execute(() -> {
            List<BudgetAccount> accountList = repository.findActiveAccounts();
            if (accountList.isEmpty()) {
                repository.insertAccount(new BudgetAccount("Girokonto"));
                repository.insertAccount(new BudgetAccount("Tagesgeld"));
                repository.insertCategory(new BudgetCategory("Sonstiges", "EXPENSE"));
                repository.insertCategory(new BudgetCategory("Gehalt", "INCOME"));
                accountList = repository.findActiveAccounts();
            }
            ensureDefaultCategories();
            if (repository.findAllTransactions().isEmpty()) {
                String accountId = accountList.get(0).id;
                LocalDate today = LocalDate.now();
                seedDemoTransactions(accountId, today);
            }
            List<BudgetCategory> cats = repository.getActiveCategories();
            List<BudgetAccount> finalAccountList = repository.findActiveAccounts();

            String selected = selectedAccountId.getValue();
            if ((selected == null || selected.isBlank()) && !finalAccountList.isEmpty()) {
                selected = finalAccountList.get(0).id;
            }
            String finalSelected = selected;
            postToMain.accept(() -> {
                categories.setValue(cats);
                accounts.setValue(finalAccountList);
                if (selectedAccountId.getValue() == null && finalSelected != null) {
                    selectedAccountId.setValue(finalSelected);
                }
            });
            loadOverviewOnExecutor();
        });
    }

    private void ensureDefaultCategories() {
        List<BudgetCategory> existing = repository.getActiveCategories();
        if (!existing.isEmpty()) {
            return;
        }
        repository.insertCategory(new BudgetCategory("Sonstiges", "EXPENSE", "🏷️", "#9E9E9E"));
        repository.insertCategory(new BudgetCategory("Miete", "EXPENSE", "🏠", "#FF7043"));
        repository.insertCategory(new BudgetCategory("Lebensmittel", "EXPENSE", "🛒", "#8BC34A"));
        repository.insertCategory(new BudgetCategory("Mobilität", "EXPENSE", "🚗", "#03A9F4"));
        repository.insertCategory(new BudgetCategory("Freizeit", "EXPENSE", "🎉", "#AB47BC"));
        repository.insertCategory(new BudgetCategory("Gehalt", "INCOME", "💰", "#4CAF50"));
    }

    private String resolveSelectedAccountId(List<BudgetAccount> fallbackAccounts) {
        String selected = selectedAccountId.getValue();
        if (selected != null && !selected.isBlank()) {
            return selected;
        }
        if (!fallbackAccounts.isEmpty()) {
            return fallbackAccounts.get(0).id;
        }
        return null;
    }

    private void seedDemoTransactions(String accountId, LocalDate reference) {
        List<BudgetCategory> categories = repository.getActiveCategories();
        String incomeCategoryId = findCategoryIdByName(categories, "Gehalt");
        String housingCategoryId = findCategoryIdByName(categories, "Miete");
        String groceryCategoryId = findCategoryIdByName(categories, "Lebensmittel");
        String mobilityCategoryId = findCategoryIdByName(categories, "Mobilität");
        String leisureCategoryId = findCategoryIdByName(categories, "Freizeit");
        String otherCategoryId = findCategoryIdByName(categories, "Sonstiges");

        int maxDay = reference.getDayOfMonth();
        BudgetTransactionEntity.TransactionType income = BudgetTransactionEntity.TransactionType.INCOME;
        BudgetTransactionEntity.TransactionType expense = BudgetTransactionEntity.TransactionType.EXPENSE;
        List<BudgetTransactionEntity> entities = new ArrayList<>();
        addDemoTx(entities, accountId, incomeCategoryId, reference,  1, income,  240000, "Gehalt",       maxDay);
        addDemoTx(entities, accountId, housingCategoryId, reference,  2, expense,  85000, "Miete",         maxDay);
        addDemoTx(entities, accountId, groceryCategoryId, reference,  3, expense,   7840, "Lebensmittel",  maxDay);
        addDemoTx(entities, accountId, otherCategoryId, reference,  5, expense,   4290, "Strom",         maxDay);
        addDemoTx(entities, accountId, otherCategoryId, reference,  8, expense,   2999, "Internet",      maxDay);
        addDemoTx(entities, accountId, leisureCategoryId, reference, 10, expense,   1990, "Fitnessstudio", maxDay);
        addDemoTx(entities, accountId, leisureCategoryId, reference, 15, expense,   3450, "Restaurant",    maxDay);
        addDemoTx(entities, accountId, mobilityCategoryId, reference, 18, expense,   6520, "Tankstelle",    maxDay);
        repository.saveTransactions(entities);
    }

    private void addDemoTx(List<BudgetTransactionEntity> out, String accountId, String categoryId,
                           LocalDate ref, int day,
                           BudgetTransactionEntity.TransactionType type,
                           long amountCents, String note, int maxDay) {
        if (day > maxDay) return;
        LocalDate date = ref.withDayOfMonth(day);
        BudgetTransactionEntity entity = new BudgetTransactionEntity(
                accountId, categoryId, type, amountCents, date, YearMonth.from(date).toString());
        entity.note = note;
        out.add(entity);
    }

    private String findCategoryIdByName(List<BudgetCategory> categories, String name) {
        for (BudgetCategory category : categories) {
            if (name.equals(category.name)) {
                return category.id;
            }
        }
        return null;
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
        postToMain.accept(() -> uiState.setValue(BudgetUiState.LOADING));
        executor.execute(this::loadOverviewOnExecutor);
    }

    private void loadOverviewOnExecutor() {
        YearMonth month = currentMonth.getValue();
        if (month == null) month = YearMonth.now();
        String yearMonthStr = month.toString();

        AccountContext accountContext = resolveAccountContext();
        if (accountContext.accountId == null) {
            postToMain.accept(() -> {
                uiState.setValue(BudgetUiState.EMPTY);
                statusMessage.setValue("Kein Konto vorhanden.");
                accounts.setValue(new ArrayList<>());
                chartPoints.setValue(new ArrayList<>());
            });
            return;
        }

        postToMain.accept(() -> {
            accounts.setValue(accountContext.accounts);
            if (selectedAccountId.getValue() == null) {
                selectedAccountId.setValue(accountContext.accountId);
            }
        });

        List<MonthlyTransactionOverviewItem> items =
                repository.getMonthlyOverviewForAccount(yearMonthStr, accountContext.accountId);

        List<BudgetTransactionRow> rows = buildTransactionRows(items);
        BudgetSummaryData summary = computeSummary(items, accountContext.accountId);
        List<BudgetChartPoint> balancePoints = loadBalanceChartData(accountContext.accountId);

        publishOverviewState(rows, balancePoints, summary);
        loadLimitsOnExecutor();
    }

    private AccountContext resolveAccountContext() {
        List<BudgetAccount> accountList = repository.findActiveAccounts();
        String accountId = resolveSelectedAccountId(accountList);
        return new AccountContext(accountList, accountId);
    }

    private List<BudgetTransactionRow> buildTransactionRows(List<MonthlyTransactionOverviewItem> items) {
        List<BudgetTransactionRow> rows = new ArrayList<>();
        for (MonthlyTransactionOverviewItem item : items) {
            boolean isExpense = "EXPENSE".equals(item.type);
            rows.add(new BudgetTransactionRow(
                    item.transactionId,
                    buildTransactionLabel(item),
                    formatTransactionAmount(item.amountCents, isExpense),
                    isExpense,
                    item.categoryColorHex,
                    item.amountCents,
                    isExpense ? BudgetTransactionEntity.TransactionType.EXPENSE
                            : BudgetTransactionEntity.TransactionType.INCOME,
                    item.categoryId,
                    item.note,
                    item.bookingDate,
                    item.accountId
            ));
        }
        return rows;
    }

    private BudgetSummaryData computeSummary(List<MonthlyTransactionOverviewItem> items, String accountId) {
        long freeBudgetCents = calculateFreeBudgetUseCase.execute(accountId, LocalDate.now(), 7);
        return summaryPresentationMapper.toSummary(items, freeBudgetCents);
    }

    private void publishOverviewState(List<BudgetTransactionRow> rows,
                                      List<BudgetChartPoint> balancePoints,
                                      BudgetSummaryData summary) {
        postToMain.accept(() -> {
            transactions.setValue(rows);
            chartPoints.setValue(balancePoints);
            summaryData.setValue(summary);
            if (!rows.isEmpty()) {
                uiState.setValue(BudgetUiState.CONTENT);
                statusMessage.setValue("Letzte Buchungen");
            } else {
                uiState.setValue(BudgetUiState.EMPTY);
                statusMessage.setValue("Noch keine Buchungen. Starte mit \"Transaktion hinzufügen\".");
            }
        });
    }

    private String buildTransactionLabel(MonthlyTransactionOverviewItem item) {
        if ("INTERNAL_TRANSFER".equals(item.transactionKind)) {
            return item.note != null && !item.note.isBlank() ? "Überweisung · " + item.note : "Überweisung";
        }
        if (item.categoryName != null) {
            String icon = item.categoryIcon != null && !item.categoryIcon.trim().isEmpty()
                    ? item.categoryIcon : BudgetCategory.DEFAULT_ICON;
            return icon + " " + item.categoryName;
        }
        if (item.note != null) {
            return item.note;
        }
        return "Buchung";
    }

    private String formatTransactionAmount(long amountCents, boolean isExpense) {
        return String.format(
                Locale.GERMAN,
                "%s%.2f €",
                isExpense ? "-" : "+",
                amountCents / 100.0
        );
    }

    private static class AccountContext {
        private final List<BudgetAccount> accounts;
        private final String accountId;

        private AccountContext(List<BudgetAccount> accounts, String accountId) {
            this.accounts = accounts;
            this.accountId = accountId;
        }
    }

    private List<BudgetChartPoint> loadBalanceChartData(String accountId) {
        TimeRangeFilter filter = timeRangeFilter.getValue();
        if (filter == null) filter = TimeRangeFilter.DAYS_30;

        long openingBalanceCents = 0;
        List<BalanceTimelinePoint> series;
        LocalDate now = LocalDate.now();

        if (filter == TimeRangeFilter.DAYS_30) {
            LocalDate fromDate = now.minusDays(29);
            long startBalance = openingBalanceCents
                    + repository.getNetAmountBeforeDateForAccount(accountId, fromDate);
            List<AccountDailyDeltaPoint> deltas =
                    repository.getDailyDeltasForAccount(accountId, fromDate, now);
            series = balanceTimelineService.reconstructDaily(fromDate, now, startBalance, deltas);
        } else {
            int months = filter == TimeRangeFilter.MONTHS_3 ? 3 : 12;
            YearMonth toMonth = YearMonth.from(now);
            YearMonth fromMonth = toMonth.minusMonths(months - 1L);
            LocalDate startDate = fromMonth.atDay(1);
            long startBalance = openingBalanceCents
                    + repository.getNetAmountBeforeDateForAccount(accountId, startDate);
            List<AccountMonthlyDeltaPoint> deltas = repository.getMonthlyDeltasForAccount(
                    accountId,
                    fromMonth.toString(),
                    toMonth.toString()
            );
            series = balanceTimelineService.reconstructMonthly(fromMonth, toMonth, startBalance, deltas);
        }

        return chartStateMapper.map(filter, series);
    }

    public void addTransaction(String amountStr, boolean isExpense, String categoryId,
                               String note, LocalDate date) {
        executor.execute(() -> {
            Long amountCents = parseAmountCents(amountStr);
            if (amountCents == null) {
                return;
            }

            List<BudgetAccount> accountList = repository.findActiveAccounts();
            String accountId = resolveSelectedAccountId(accountList);
            if (accountId == null) return;

            BudgetTransactionEntity.TransactionType type = isExpense
                    ? BudgetTransactionEntity.TransactionType.EXPENSE
                    : BudgetTransactionEntity.TransactionType.INCOME;

            String yearMonthStr = YearMonth.from(date).toString();
            BudgetTransactionEntity entity = new BudgetTransactionEntity(
                    accountId, categoryId, type, amountCents, date, yearMonthStr);
            entity.note = note;

            repository.saveTransaction(entity);
            loadOverviewOnExecutor();
        });
    }

    public void updateTransaction(String transactionId, String amountStr, boolean isExpense,
                                  String categoryId, String note, LocalDate date, String accountId) {
        executor.execute(() -> {
            Long amountCents = parseAmountCents(amountStr);
            if (amountCents == null) {
                return;
            }

            BudgetTransactionEntity existing = repository.findTransactionById(transactionId);
            BudgetTransactionEntity entity;
            if (existing != null) {
                entity = existing;
            } else {
                entity = new BudgetTransactionEntity(
                        accountId,
                        categoryId,
                        isExpense ? BudgetTransactionEntity.TransactionType.EXPENSE
                                : BudgetTransactionEntity.TransactionType.INCOME,
                        amountCents,
                        date,
                        YearMonth.from(date).toString());
                entity.id = transactionId;
            }

            entity.accountId = accountId;
            entity.categoryId = categoryId;
            entity.type = isExpense
                    ? BudgetTransactionEntity.TransactionType.EXPENSE
                    : BudgetTransactionEntity.TransactionType.INCOME;
            entity.amountCents = amountCents;
            entity.bookingDate = date;
            entity.yearMonth = YearMonth.from(date).toString();
            entity.note = (note == null || note.trim().isEmpty()) ? null : note.trim();

            repository.updateTransaction(entity);
            loadOverviewOnExecutor();
        });
    }

    public void addTransfer(String sourceAccountId,
                            String targetAccountId,
                            String amountStr,
                            LocalDate date,
                            String note) {
        executor.execute(() -> {
            Long amountCents = parseAmountCents(amountStr);
            if (amountCents == null) {
                return;
            }

            // Create flow: validates input and persists a new transfer.
            CreateTransferUseCase.Result result = createTransferUseCase.execute(
                    sourceAccountId,
                    targetAccountId,
                    amountCents,
                    date,
                    note
            );
            if (!result.success()) {
                postToMain.accept(() -> {
                    uiState.setValue(BudgetUiState.ERROR);
                    statusMessage.setValue(result.errorMessage());
                });
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
        executor.execute(() -> {
            Long amountCents = parseAmountCents(amountStr);
            if (amountCents == null) {
                return;
            }

            // Update flow: validates input and updates the existing transfer pair.
            CreateTransferUseCase.Result result = createTransferUseCase.update(
                    transactionId,
                    sourceAccountId,
                    targetAccountId,
                    amountCents,
                    date,
                    note
            );
            if (!result.success()) {
                postToMain.accept(() -> {
                    uiState.setValue(BudgetUiState.ERROR);
                    statusMessage.setValue(result.errorMessage());
                });
                return;
            }
            loadOverviewOnExecutor();
        });
    }

    public void deleteTransaction(String transactionId) {
        executor.execute(() -> {
            repository.deleteTransaction(transactionId);
            loadOverviewOnExecutor();
        });
    }

    public void importFromCsv(String fileName, byte[] bytes, String mimeType) {
        postToMain.accept(() -> uiState.setValue(BudgetUiState.LOADING));

        String accountId;
        List<BudgetAccount> accountList = repository.findActiveAccounts();
        accountId = resolveSelectedAccountId(accountList);
        if (accountId == null) {
            postToMain.accept(() -> {
                uiState.setValue(BudgetUiState.ERROR);
                statusMessage.setValue("Kein Konto vorhanden.");
            });
            return;
        }

        importUseCase.executeAsync(accountId, fileName, bytes, mimeType, new BudgetImportUseCase.ImportCallback() {
            @Override
            public void onProgress(String message) {
                postToMain.accept(() -> statusMessage.setValue(message));
            }

            @Override
            public void onSuccess(BudgetImportUseCase.ImportResult result) {
                loadOverview();
                postToMain.accept(() -> {
                    String msg = result.newTransactions() + " neu, "
                            + result.duplicates() + " Duplikate, "
                            + result.autoCategorized() + " auto-kategorisiert.";
                    statusMessage.setValue(msg);
                    importDialogState.setValue(importDialogStateMapper.map(result));
                });
            }

            @Override
            public void onError(String errorMessage) {
                postToMain.accept(() -> {
                    uiState.setValue(BudgetUiState.ERROR);
                    statusMessage.setValue("Import fehlgeschlagen: " + errorMessage);
                });
            }
        });
    }

    public void onImportReadFailed() {
        postToMain.accept(() -> {
            uiState.setValue(BudgetUiState.ERROR);
            statusMessage.setValue("Datei konnte nicht gelesen werden. Prüfe Berechtigung und Dateiformat.");
        });
    }

    public void setImportStatus(String message) {
        postToMain.accept(() -> statusMessage.setValue(message));
    }

    public void applyRecurringSuggestions(List<RecurringSuggestion> suggestions) {
        List<BudgetAccount> accountList = repository.findActiveAccounts();
        String accountId = resolveSelectedAccountId(accountList);
        if (accountId == null) return;

        applyRecurringUseCase.executeAsync(
                accountId,
                suggestions,
                () -> postToMain.accept(this::loadOverview),
                error -> postToMain.accept(() -> statusMessage.setValue("Fehler: " + error))
        );
    }

    public void retry() {
        loadOverview();
    }

    private Long parseAmountCents(String amountStr) {
        if (amountStr == null) {
            showInvalidAmountError();
            return null;
        }

        String normalized = amountStr.trim()
                .replace("\u00A0", "")
                .replace(" ", "");

        if (normalized.isEmpty()) {
            showInvalidAmountError();
            return null;
        }

        int commaIndex = normalized.lastIndexOf(',');
        int dotIndex = normalized.lastIndexOf('.');

        if (commaIndex >= 0 && dotIndex >= 0) {
            int decimalIndex = Math.max(commaIndex, dotIndex);
            char decimalSeparator = normalized.charAt(decimalIndex);
            char thousandsSeparator = decimalSeparator == ',' ? '.' : ',';
            normalized = normalized.replace(String.valueOf(thousandsSeparator), "")
                    .replace(decimalSeparator, '.');
        } else if (commaIndex >= 0) {
            normalized = normalized.replace(',', '.');
        }

        try {
            BigDecimal amount = new BigDecimal(normalized);
            return amount
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            showInvalidAmountError();
            return null;
        }
    }

    private void showInvalidAmountError() {
        postToMain.accept(() -> {
            uiState.setValue(BudgetUiState.ERROR);
            statusMessage.setValue("Ungültiger Betrag");
        });
    }

    private void loadLimitsOnExecutor() {
        YearMonth month = currentMonth.getValue();
        if (month == null) month = YearMonth.now();
        String yearMonthStr = month.toString();

        List<CategorySpendTotal> totals = repository.getCategorySpendTotals(yearMonthStr);
        List<BudgetLimitBar> bars = summaryPresentationMapper.toLimitBars(
                totals,
                repository::getEffectiveLimitCents,
                yearMonthStr
        );
        postToMain.accept(() -> budgetLimits.setValue(bars));
    }

    public void saveBudgetLimit(String categoryId, double amountEuros) {
        saveBudgetLimit(categoryId, amountEuros, false, 0L);
    }

    public void saveBudgetLimit(String categoryId, double amountEuros, boolean rolloverEnabled, long rolloverCarryoverCents) {
        executor.execute(() -> {
            YearMonth month = currentMonth.getValue();
            if (month == null) month = YearMonth.now();
            String yearMonthStr = month.toString();
            BudgetLimit limit = new BudgetLimit(categoryId, yearMonthStr, amountEuros);
            limit.rolloverEnabled = rolloverEnabled;
            limit.rolloverCarryoverCents = rolloverCarryoverCents;
            repository.saveBudgetLimit(limit);
            loadOverviewOnExecutor();
        });
    }
}
