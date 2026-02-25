package com.autosecretary.features.budget.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.autosecretary.features.budget.application.importing.ApplyRecurringSuggestionsUseCase;
import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.application.importing.StatementFileParser;
import com.autosecretary.features.budget.data.BudgetAccount;
import com.autosecretary.features.budget.data.BudgetCategory;
import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetTransactionEntity;
import com.autosecretary.features.budget.data.CategorySpendTotal;
import com.autosecretary.features.budget.data.MonthlyTransactionOverviewItem;
import com.autosecretary.features.budget.domain.BudgetRepository;
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

    public static class BudgetTransactionRow {
        private final String transactionId;
        private final String label;
        private final String amount;
        private final boolean isExpense;
        private final String categoryColorHex;

        public BudgetTransactionRow(String transactionId, String label, String amount,
                                    boolean isExpense, String categoryColorHex) {
            this.transactionId = transactionId;
            this.label = label;
            this.amount = amount;
            this.isExpense = isExpense;
            this.categoryColorHex = categoryColorHex;
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
    }

    public static class BudgetSummaryData {
        private final long incomeCents;
        private final long expenseCents;
        private final long netCents;

        public BudgetSummaryData(long incomeCents, long expenseCents) {
            this.incomeCents = incomeCents;
            this.expenseCents = expenseCents;
            this.netCents = incomeCents - expenseCents;
        }

        public long getIncomeCents() { return incomeCents; }
        public long getExpenseCents() { return expenseCents; }
        public long getNetCents() { return netCents; }
    }

    public static class BudgetLimitBar {
        private final String categoryId;
        private final String categoryName;
        private final String categoryColorHex;
        private final long spentCents;
        private final double limitEuros;
        private final int percentage;

        public BudgetLimitBar(String categoryId, String categoryName, String categoryColorHex,
                              long spentCents, double limitEuros) {
            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.categoryColorHex = categoryColorHex;
            this.spentCents = spentCents;
            this.limitEuros = limitEuros;
            this.percentage = limitEuros > 0
                    ? (int) ((spentCents / 100.0) / limitEuros * 100)
                    : 0;
        }

        public String getCategoryId() { return categoryId; }
        public String getCategoryName() { return categoryName; }
        public String getCategoryColorHex() { return categoryColorHex; }
        public long getSpentCents() { return spentCents; }
        public double getLimitEuros() { return limitEuros; }
        public int getPercentage() { return percentage; }
    }

    private static final DateTimeFormatter MONTH_FORMATTER =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN);

    private final MutableLiveData<String> title = new MutableLiveData<>("Budgetübersicht");
    private final MutableLiveData<BudgetSummaryData> summaryData = new MutableLiveData<>();
    private final MutableLiveData<BudgetUiState> uiState = new MutableLiveData<>(BudgetUiState.LOADING);
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>("");
    private final MutableLiveData<List<BudgetTransactionRow>> transactions = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<BudgetImportUseCase.ImportResult> importResult = new MutableLiveData<>();
    private final MutableLiveData<YearMonth> currentMonth = new MutableLiveData<>(YearMonth.now());
    private final MutableLiveData<List<BudgetCategory>> categories = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<BudgetLimitBar>> budgetLimits = new MutableLiveData<>(new ArrayList<>());

    private final BudgetRepository repository;
    private final StatementFileParser parser;
    private final ExecutorService executor;
    private final Consumer<Runnable> postToMain;
    private final BudgetImportUseCase importUseCase;
    private final ApplyRecurringSuggestionsUseCase applyRecurringUseCase;

    public BudgetViewModel(BudgetRepository repository,
                           StatementFileParser parser,
                           ExecutorService executor,
                           Consumer<Runnable> postToMain,
                           BudgetImportUseCase importUseCase,
                           ApplyRecurringSuggestionsUseCase applyRecurringUseCase) {
        this.repository = repository;
        this.parser = parser;
        this.executor = executor;
        this.postToMain = postToMain;
        this.importUseCase = importUseCase;
        this.applyRecurringUseCase = applyRecurringUseCase;
        ensureDefaultData();
    }

    public LiveData<String> getTitle() {
        return title;
    }

    public LiveData<BudgetSummaryData> getSummaryData() {
        return summaryData;
    }

    public LiveData<BudgetUiState> getUiState() {
        return uiState;
    }

    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }

    public LiveData<List<BudgetTransactionRow>> getTransactions() {
        return transactions;
    }

    public LiveData<BudgetImportUseCase.ImportResult> getImportResult() {
        return importResult;
    }

    public LiveData<YearMonth> getCurrentMonth() {
        return currentMonth;
    }

    public LiveData<List<BudgetCategory>> getCategories() {
        return categories;
    }

    public LiveData<List<BudgetLimitBar>> getLimits() {
        return budgetLimits;
    }

    public void clearImportResult() {
        importResult.setValue(null);
    }

    private void ensureDefaultData() {
        executor.execute(() -> {
            List<BudgetAccount> accounts = repository.findActiveAccounts();
            if (accounts.isEmpty()) {
                repository.insertAccount(new BudgetAccount("Girokonto"));
            }
            ensureDefaultCategories();
            if (repository.findAllTransactions().isEmpty()) {
                String accountId = repository.findActiveAccounts().get(0).id;
                LocalDate today = LocalDate.now();
                seedDemoTransactions(accountId, today);
            }
            List<BudgetCategory> cats = repository.getActiveCategories();
            postToMain.accept(() -> categories.setValue(cats));
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
        List<MonthlyTransactionOverviewItem> items =
                repository.getMonthlyOverview(yearMonthStr);

        List<BudgetTransactionRow> rows = new ArrayList<>();
        long totalIncomeCents = 0;
        long totalExpenseCents = 0;

        for (MonthlyTransactionOverviewItem item : items) {
            boolean isExpense = "EXPENSE".equals(item.type);
            String label;
            if (item.categoryName != null) {
                String icon = item.categoryIcon != null && !item.categoryIcon.trim().isEmpty()
                        ? item.categoryIcon : BudgetCategory.DEFAULT_ICON;
                label = icon + " " + item.categoryName;
            } else if (item.note != null) {
                label = item.note;
            } else {
                label = "Buchung";
            }
            String formattedAmount = String.format(
                    Locale.GERMAN,
                    "%s%.2f €",
                    isExpense ? "-" : "+",
                    item.amountCents / 100.0
            );
            rows.add(new BudgetTransactionRow(item.transactionId, label, formattedAmount, isExpense, item.categoryColorHex));

            if (isExpense) {
                totalExpenseCents += item.amountCents;
            } else {
                totalIncomeCents += item.amountCents;
            }
        }

        long finalTotalIncomeCents = totalIncomeCents;
        long finalTotalExpenseCents = totalExpenseCents;
        List<BudgetTransactionRow> finalRows = rows;

        postToMain.accept(() -> {
            transactions.setValue(finalRows);
            summaryData.setValue(new BudgetSummaryData(finalTotalIncomeCents, finalTotalExpenseCents));
            if (!finalRows.isEmpty()) {
                uiState.setValue(BudgetUiState.CONTENT);
                statusMessage.setValue("Letzte Buchungen");
            } else {
                uiState.setValue(BudgetUiState.EMPTY);
                statusMessage.setValue("Noch keine Buchungen. Starte mit \"Transaktion hinzufügen\".");
            }
        });
        loadLimitsOnExecutor();
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

            List<BudgetAccount> accounts = repository.findActiveAccounts();
            String accountId = accounts.get(0).id;

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

    public void deleteTransaction(String transactionId) {
        executor.execute(() -> {
            repository.deleteTransaction(transactionId);
            loadOverviewOnExecutor();
        });
    }

    public void importFromCsv(String fileName, byte[] bytes, String mimeType) {
        postToMain.accept(() -> uiState.setValue(BudgetUiState.LOADING));

        String accountId;
        List<BudgetAccount> accounts = repository.findActiveAccounts();
        if (!accounts.isEmpty()) {
            accountId = accounts.get(0).id;
        } else {
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
                    String msg = result.newTransactions() + " neu, " + result.duplicates() + " Duplikate.";
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
            statusMessage.setValue("Datei konnte nicht gelesen werden.");
        });
    }

    public void applyRecurringSuggestions(List<RecurringSuggestion> suggestions) {
        List<BudgetAccount> accounts = repository.findActiveAccounts();
        if (accounts.isEmpty()) return;
        String accountId = accounts.get(0).id;

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
                bars.add(new BudgetLimitBar(
                        total.categoryId, label, total.categoryColorHex,
                        total.spentCents, total.limitAmount));
            }
        }
        postToMain.accept(() -> budgetLimits.setValue(bars));
    }

    public void saveBudgetLimit(String categoryId, double amountEuros) {
        executor.execute(() -> {
            YearMonth month = currentMonth.getValue();
            if (month == null) month = YearMonth.now();
            String yearMonthStr = month.toString();
            BudgetLimit limit = new BudgetLimit(categoryId, yearMonthStr, amountEuros);
            repository.saveBudgetLimit(limit);
            loadOverviewOnExecutor();
        });
    }
}
