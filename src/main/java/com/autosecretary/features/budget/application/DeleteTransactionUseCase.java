package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetRepository;
import com.autosecretary.features.budget.data.Transaction;
import com.autosecretary.features.budget.domain.AccountBalanceRecalculationService;
import com.autosecretary.features.budget.domain.BudgetConsumptionService;

import java.time.LocalDate;

public class DeleteTransactionUseCase {
    private final BudgetRepository repository;
    private final AccountBalanceRecalculationService balanceService;
    private final BudgetConsumptionService consumptionService;

    public DeleteTransactionUseCase(BudgetRepository repository,
                                    AccountBalanceRecalculationService balanceService,
                                    BudgetConsumptionService consumptionService) {
        this.repository = repository;
        this.balanceService = balanceService;
        this.consumptionService = consumptionService;
    }

    public void execute(Transaction transaction) {
        if (transaction == null || transaction.id == null) {
            return;
        }

        repository.deleteTransaction(transaction.id);

        if (transaction.accountId != null) {
            var account = repository.findAccountById(transaction.accountId);
            if (account != null) {
                balanceService.recalculateBalance(account, repository.findTransactionsForAccount(transaction.accountId));
                repository.saveAccount(account);
            }
        }

        if (!transaction.isIncome && transaction.categoryId != null && transaction.transactionDate != null) {
            String yearMonth = toYearMonth(transaction.transactionDate);
            BudgetLimit limit = repository.findBudgetLimit(transaction.categoryId, yearMonth);
            if (limit != null) {
                limit.spentCents = consumptionService
                        .calculateMonthlyConsumption(limit, repository.findAllTransactions())
                        .spentCents();
                repository.saveBudgetLimit(limit);
            }
        }
    }

    private String toYearMonth(LocalDate date) {
        return String.format("%d-%02d", date.getYear(), date.getMonthValue());
    }
}
