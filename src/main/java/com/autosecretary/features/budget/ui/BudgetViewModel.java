package com.autosecretary.features.budget.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

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

        public BudgetTransactionRow(String label, String amount) {
            this.label = label;
            this.amount = amount;
        }

        public String getLabel() {
            return label;
        }

        public String getAmount() {
            return amount;
        }
    }

    private final MutableLiveData<String> title = new MutableLiveData<>("Budgetübersicht");
    private final MutableLiveData<String> summary = new MutableLiveData<>("Erfasse Buchungen oder importiere einen Kontoauszug.");
    private final MutableLiveData<BudgetUiState> uiState = new MutableLiveData<>(BudgetUiState.LOADING);
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>("");
    private final MutableLiveData<List<BudgetTransactionRow>> transactions = new MutableLiveData<>(new ArrayList<>());

    public BudgetViewModel() {
        loadOverview();
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

    public void loadOverview() {
        List<BudgetTransactionRow> rows = transactions.getValue();
        if (rows == null || rows.isEmpty()) {
            uiState.setValue(BudgetUiState.EMPTY);
            statusMessage.setValue("Noch keine Buchungen. Starte mit \"Transaktion hinzufügen\".");
        } else {
            uiState.setValue(BudgetUiState.CONTENT);
            statusMessage.setValue("Letzte Buchungen");
        }
    }

    public void addQuickTransaction() {
        List<BudgetTransactionRow> current = transactions.getValue();
        if (current == null) {
            current = new ArrayList<>();
        } else {
            current = new ArrayList<>(current);
        }
        current.add(0, new BudgetTransactionRow("Manuelle Buchung", "-10,00 €"));
        transactions.setValue(current);
        uiState.setValue(BudgetUiState.CONTENT);
        statusMessage.setValue("Buchung hinzugefügt.");
    }

    public void importStatement() {
        uiState.setValue(BudgetUiState.ERROR);
        statusMessage.setValue("Import folgt in einem nächsten Schritt. Nutze vorerst \"Transaktion hinzufügen\".");
    }

    public void retry() {
        loadOverview();
    }
}
