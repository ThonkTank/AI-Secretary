package com.autosecretary.features.budget.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.autosecretary.features.budget.application.importing.BudgetTransactionMapper;
import com.autosecretary.features.budget.application.importing.StatementFileParser;
import com.autosecretary.features.budget.data.BudgetAccount;
import com.autosecretary.features.budget.data.BudgetCategory;
import com.autosecretary.features.budget.data.BudgetTransactionEntity;
import com.autosecretary.features.budget.data.MonthlyTransactionOverviewItem;
import com.autosecretary.features.budget.domain.BudgetRepository;

import java.time.LocalDate;
import java.time.YearMonth;
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
        private final String label;
        private final String amount;
        private final boolean isExpense;

        public BudgetTransactionRow(String label, String amount, boolean isExpense) {
            this.label = label;
            this.amount = amount;
            this.isExpense = isExpense;
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
    }

    private final MutableLiveData<String> title = new MutableLiveData<>("Budgetübersicht");
    private final MutableLiveData<String> summary = new MutableLiveData<>("Erfasse Buchungen oder importiere einen Kontoauszug.");
    private final MutableLiveData<BudgetUiState> uiState = new MutableLiveData<>(BudgetUiState.LOADING);
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>("");
    private final MutableLiveData<List<BudgetTransactionRow>> transactions = new MutableLiveData<>(new ArrayList<>());

    private final BudgetRepository repository;
    private final StatementFileParser parser;
    private final BudgetTransactionMapper transactionMapper = new BudgetTransactionMapper();
    private final ExecutorService executor;
    private final Consumer<Runnable> postToMain;

    public BudgetViewModel(BudgetRepository repository,
                           StatementFileParser parser,
                           ExecutorService executor,
                           Consumer<Runnable> postToMain) {
        this.repository = repository;
        this.parser = parser;
        this.executor = executor;
        this.postToMain = postToMain;
        ensureDefaultData();
    }

    public LiveData<String> getTitle() {
        return title;
    }

    public LiveData<String> getSummary() {
        return summary;
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

    private void ensureDefaultData() {
        executor.execute(() -> {
            List<BudgetAccount> accounts = repository.findActiveAccounts();
            if (accounts.isEmpty()) {
                repository.insertAccount(new BudgetAccount("Girokonto"));
                repository.insertCategory(new BudgetCategory("Sonstiges", "EXPENSE"));
                repository.insertCategory(new BudgetCategory("Gehalt", "INCOME"));
            }
            if (repository.findAllTransactions().isEmpty()) {
                String accountId = repository.findActiveAccounts().get(0).id;
                LocalDate today = LocalDate.now();
                seedDemoTransactions(accountId, today);
            }
            loadOverviewOnExecutor();
        });
    }

    private void seedDemoTransactions(String accountId, LocalDate reference) {
        int maxDay = reference.getDayOfMonth();
        BudgetTransactionEntity.TransactionType income = BudgetTransactionEntity.TransactionType.INCOME;
        BudgetTransactionEntity.TransactionType expense = BudgetTransactionEntity.TransactionType.EXPENSE;
        List<BudgetTransactionEntity> entities = new ArrayList<>();
        addDemoTx(entities, accountId, reference,  1, income,  240000, "Gehalt",        maxDay);
        addDemoTx(entities, accountId, reference,  2, expense,  85000, "Miete",          maxDay);
        addDemoTx(entities, accountId, reference,  3, expense,   7840, "Lebensmittel",   maxDay);
        addDemoTx(entities, accountId, reference,  5, expense,   4290, "Strom",          maxDay);
        addDemoTx(entities, accountId, reference,  8, expense,   2999, "Internet",       maxDay);
        addDemoTx(entities, accountId, reference, 10, expense,   1990, "Fitnessstudio",  maxDay);
        addDemoTx(entities, accountId, reference, 15, expense,   3450, "Restaurant",     maxDay);
        addDemoTx(entities, accountId, reference, 18, expense,   6520, "Tankstelle",     maxDay);
        repository.saveTransactions(entities);
    }

    private void addDemoTx(List<BudgetTransactionEntity> out, String accountId,
                            LocalDate ref, int day,
                            BudgetTransactionEntity.TransactionType type,
                            long amountCents, String note, int maxDay) {
        if (day > maxDay) return;
        LocalDate date = ref.withDayOfMonth(day);
        BudgetTransactionEntity entity = new BudgetTransactionEntity(
                accountId, null, type, amountCents, date, YearMonth.from(date).toString());
        entity.note = note;
        out.add(entity);
    }

    public void loadOverview() {
        postToMain.accept(() -> uiState.setValue(BudgetUiState.LOADING));
        executor.execute(this::loadOverviewOnExecutor);
    }

    private void loadOverviewOnExecutor() {
        String currentYearMonth = YearMonth.now().toString();
        List<MonthlyTransactionOverviewItem> items =
                repository.getMonthlyOverview(currentYearMonth);

        List<BudgetTransactionRow> rows = new ArrayList<>();
        long totalIncomeCents = 0;
        long totalExpenseCents = 0;

        for (MonthlyTransactionOverviewItem item : items) {
            boolean isExpense = "EXPENSE".equals(item.type);
            String label;
            if (item.categoryName != null) {
                label = item.categoryName;
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
            rows.add(new BudgetTransactionRow(label, formattedAmount, isExpense));

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
            if (!finalRows.isEmpty()) {
                uiState.setValue(BudgetUiState.CONTENT);
                statusMessage.setValue("Letzte Buchungen");
                summary.setValue(String.format(
                        Locale.GERMAN,
                        "Einnahmen: +%.2f € | Ausgaben: -%.2f €",
                        finalTotalIncomeCents / 100.0,
                        finalTotalExpenseCents / 100.0
                ));
            } else {
                uiState.setValue(BudgetUiState.EMPTY);
                statusMessage.setValue("Noch keine Buchungen. Starte mit \"Transaktion hinzufügen\".");
                summary.setValue("Erfasse Buchungen oder importiere einen Kontoauszug.");
            }
        });
    }

    public void addTransaction(String amountStr, boolean isExpense, String note, LocalDate date) {
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
                    accountId, null, type, amountCents, date, yearMonthStr);
            entity.note = note;

            repository.saveTransaction(entity);
            loadOverviewOnExecutor();
        });
    }

    public void importFromCsv(String fileName, byte[] bytes, String mimeType) {
        postToMain.accept(() -> uiState.setValue(BudgetUiState.LOADING));
        executor.execute(() -> {
            try {
                StatementFileParser.ParsedStatement statement =
                        parser.parse(fileName, bytes, mimeType);

                List<BudgetAccount> accounts = repository.findActiveAccounts();
                String accountId = accounts.get(0).id;

                List<BudgetTransactionEntity> entities =
                        transactionMapper.toEntities(statement.transactions(), accountId);

                repository.saveTransactions(entities);

                int count = entities.size();
                loadOverviewOnExecutor();
                postToMain.accept(() ->
                        statusMessage.setValue(count + " Buchungen importiert.")
                );
            } catch (Exception e) {
                postToMain.accept(() -> {
                    uiState.setValue(BudgetUiState.ERROR);
                    statusMessage.setValue("Import fehlgeschlagen: " + e.getMessage());
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

    public void retry() {
        loadOverview();
    }
}
