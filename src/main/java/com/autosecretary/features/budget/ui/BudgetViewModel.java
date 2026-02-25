package com.autosecretary.features.budget.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.autosecretary.features.budget.application.CreateTransferUseCase;
import com.autosecretary.features.budget.application.importing.ApplyRecurringSuggestionsUseCase;
import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.application.importing.StatementFileParser;
import com.autosecretary.features.budget.data.AccountDailyDeltaPoint;
import com.autosecretary.features.budget.data.AccountMonthlyDeltaPoint;
import com.autosecretary.features.budget.data.BudgetAccount;
import com.autosecretary.features.budget.data.BudgetCategory;
import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetTransactionEntity;
import com.autosecretary.features.budget.data.CategorySpendTotal;
import com.autosecretary.features.budget.data.MonthlyTransactionOverviewItem;
import com.autosecretary.features.budget.domain.AccountBalanceTimelineService;
import com.autosecretary.features.budget.domain.BalanceTimelinePoint;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.CalculateFreeBudgetUseCase;
import com.autosecretary.features.budget.domain.RecurringSuggestion;

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

    public static class BudgetChartPoint {
        private final String label;
        private final long balanceCents;

        public BudgetChartPoint(String label, long balanceCents) {
            this.label = label;
            this.balanceCents = balanceCents;
        }

        public String getLabel() {
            return label;
        }

        public long getBalanceCents() {
            return balanceCents;
        }
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

    public static class BudgetSummaryData {
        private final long incomeCents;
        private final long expenseCents;
        private final long netCents;
        private final long freeBudgetCents;

        public BudgetSummaryData(long incomeCents, long expenseCents) {
            this(incomeCents, expenseCents, 0L);
        }

        public BudgetSummaryData(long incomeCents, long expenseCents, long freeBudgetCents) {
            this.incomeCents = incomeCents;
            this.expenseCents = expenseCents;
            this.netCents = incomeCents - expenseCents;
            this.freeBudgetCents = freeBudgetCents;
        }

        public long getIncomeCents() { return incomeCents; }
        public long getExpenseCents() { return expenseCents; }
        public long getNetCents() { return netCents; }
        public long getFreeBudgetCents() { return freeBudgetCents; }
    }

    public static class BudgetLimitBar {
        private final String categoryId;
        private final String categoryName;
        private final String categoryColorHex;
        private final long spentCents;
        private final double baseLimitEuros;
        private final double effectiveLimitEuros;
        private final int percentage;

        public BudgetLimitBar(String categoryId, String categoryName, String categoryColorHex,
                              long spentCents, double baseLimitEuros, double effectiveLimitEuros) {
            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.categoryColorHex = categoryColorHex;
            this.spentCents = spentCents;
            this.baseLimitEuros = baseLimitEuros;
            this.effectiveLimitEuros = effectiveLimitEuros;
            this.percentage = effectiveLimitEuros > 0
                    ? (int) ((spentCents / 100.0) / effectiveLimitEuros * 100)
                    : 0;
        }

        public String getCategoryId() { return categoryId; }
        public String getCategoryName() { return categoryName; }
        public String getCategoryColorHex() { return categoryColorHex; }
        public long getSpentCents() { return spentCents; }
        public double getBaseLimitEuros() { return baseLimitEuros; }
        public double getEffectiveLimitEuros() { return effectiveLimitEuros; }
        public int getPercentage() { return percentage; }
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
    private final MutableLiveData<BudgetImportUseCase.ImportResult> importResult = new MutableLiveData<>();
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
        ensureDefaultData();
    }

    public LiveData<String> getTitle() { return title; }
    public LiveData<BudgetSummaryData> getSummaryData() { return summaryData; }
    public LiveData<BudgetUiState> getUiState() { return uiState; }
    public LiveData<String> getStatusMessage() { return statusMessage; }
    public LiveData<List<BudgetTransactionRow>> getTransactions() { return transactions; }
    public LiveData<BudgetImportUseCase.ImportResult> getImportResult() { return importResult; }
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
        importResult.setValue(null);
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
        long totalIncomeCents = 0;
        long totalExpenseCents = 0;

        for (MonthlyTransactionOverviewItem item : items) {
            if ("INTERNAL_TRANSFER".equals(item.transactionKind)) {
                continue;
            }
            if ("EXPENSE".equals(item.type)) {
                totalExpenseCents += item.amountCents;
            } else {
                totalIncomeCents += item.amountCents;
            }
        }

        long freeBudgetCents = calculateFreeBudgetUseCase.execute(accountId, LocalDate.now(), 7);
        return new BudgetSummaryData(totalIncomeCents, totalExpenseCents, freeBudgetCents);
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

        List<BudgetChartPoint> points = new ArrayList<>();
        for (BalanceTimelinePoint point : series) {
            String label = filter == TimeRangeFilter.DAYS_30
                    ? point.getDate().format(DAILY_POINT_LABEL)
                    : point.getDate().format(MONTHLY_POINT_LABEL);
            points.add(new BudgetChartPoint(label, point.getBalanceCents()));
        }
        return points;
    }

    public void addTransaction(String amountStr, boolean isExpense, String categoryId,
                               String note, LocalDate date) {
        executor.execute(() -> {
            long amountCents;
            try {
                String normalized = amountStr.replace(',', '.');
                amountCents = Math.round(Double.parseDouble(normalized) * 100);
            } catch (NumberFormatException e) {
                postToMain.accept(() -> {
                    uiState.setValue(BudgetUiState.ERROR);
                    statusMessage.setValue("Ungültiger Betrag");
                });
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
            long amountCents;
            try {
                String normalized = amountStr.replace(',', '.');
                amountCents = Math.round(Double.parseDouble(normalized) * 100);
            } catch (NumberFormatException e) {
                postToMain.accept(() -> {
                    uiState.setValue(BudgetUiState.ERROR);
                    statusMessage.setValue("Ungültiger Betrag");
                });
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
            long amountCents;
            try {
                String normalized = amountStr.replace(',', '.');
                amountCents = Math.round(Double.parseDouble(normalized) * 100);
            } catch (NumberFormatException e) {
                postToMain.accept(() -> {
                    uiState.setValue(BudgetUiState.ERROR);
                    statusMessage.setValue("Ungültiger Betrag");
                });
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
            long amountCents;
            try {
                String normalized = amountStr.replace(',', '.');
                amountCents = Math.round(Double.parseDouble(normalized) * 100);
            } catch (NumberFormatException e) {
                postToMain.accept(() -> {
                    uiState.setValue(BudgetUiState.ERROR);
                    statusMessage.setValue("Ungültiger Betrag");
                });
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
                    importResult.setValue(result);
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

    private void loadLimitsOnExecutor() {
        YearMonth month = currentMonth.getValue();
        if (month == null) month = YearMonth.now();
        String yearMonthStr = month.toString();

        List<CategorySpendTotal> totals = repository.getCategorySpendTotals(yearMonthStr);
        List<BudgetLimitBar> bars = new ArrayList<>();
        for (CategorySpendTotal total : totals) {
            if (total.limitAmount > 0) {
                String icon = total.categoryIcon != null && !total.categoryIcon.trim().isEmpty()
                        ? total.categoryIcon : BudgetCategory.DEFAULT_ICON;
                String label = icon + " " + total.categoryName;
                Long effectiveLimitCents = repository.getEffectiveLimitCents(total.categoryId, yearMonthStr);
                double effectiveLimitEuros = effectiveLimitCents != null
                        ? (effectiveLimitCents / 100.0)
                        : total.limitAmount;
                bars.add(new BudgetLimitBar(
                        total.categoryId, label, total.categoryColorHex,
                        total.spentCents, total.limitAmount, effectiveLimitEuros));
            }
        }
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
