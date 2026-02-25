package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.data.BudgetTransactionEntity;
import com.autosecretary.features.budget.domain.RecurringSuggestion;

import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BudgetImportUseCaseTest {

    @Test
    public void executeAsync_skipsDuplicatesAndSavesBatch() throws Exception {
        FakeRepo repo = new FakeRepo();
        StatementFileParser parser = new StatementFileParser();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        BudgetImportUseCase useCase = new BudgetImportUseCase(repo, parser, executor);

        String csv = "date,amountCents,payee,description,categoryId,importHash\n"
                + "2025-01-05,-1000,REWE,Food,,dup_hash\n"
                + "2025-01-06,-1200,REWE,Food,,\n";

        AtomicReference<BudgetImportUseCase.ImportResult> resultRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();

        repo.existingImportHashes.add("dup_hash");

        useCase.executeAsync(11L, "statement.csv", csv.getBytes(), "text/csv", new BudgetImportUseCase.ImportCallback() {
            @Override
            public void onProgress(String message) {
            }

            @Override
            public void onSuccess(BudgetImportUseCase.ImportResult result) {
                resultRef.set(result);
            }

            @Override
            public void onError(String errorMessage) {
                errorRef.set(errorMessage);
            }
        });

        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        Assert.assertNull(errorRef.get());
        Assert.assertNotNull(resultRef.get());
        Assert.assertEquals(2, resultRef.get().totalTransactions());
        Assert.assertEquals(1, resultRef.get().newTransactions());
        Assert.assertEquals(1, resultRef.get().duplicates());
        Assert.assertEquals(1, repo.savedTransactions.size());
        Assert.assertEquals(BudgetTransactionEntity.TransactionType.EXPENSE, repo.savedTransactions.get(0).type);
        Assert.assertEquals(1, repo.notifyCalls);
    }

    static class FakeRepo implements BudgetImportRepository {
        final List<String> existingImportHashes = new ArrayList<>();
        final List<BudgetTransactionEntity> savedTransactions = new ArrayList<>();
        int notifyCalls;

        @Override
        public ImportRecord createImport(Long accountId, String fileName, String fileHash) {
            return new ImportRecord(99L, accountId, fileName, fileHash,
                    null, null, 0, 0, 0, "PENDING", null);
        }

        @Override
        public void markImportCompleted(Long importId, int totalTransactions, int importedTransactions, int autoCategorized,
                                        LocalDate periodStart, LocalDate periodEnd) {
        }

        @Override
        public void markImportFailed(Long importId, String errorMessage) {
        }

        @Override
        public boolean existsTransactionByImportHash(String importHash) {
            return existingImportHashes.contains(importHash);
        }

        @Override
        public Long findDefaultCategoryId(boolean income) {
            return income ? 1L : 2L;
        }

        @Override
        public void saveTransactionsBatch(List<BudgetTransactionEntity> transactions) {
            savedTransactions.addAll(transactions);
        }

        @Override
        public List<BudgetTransactionEntity> loadTransactionsForAccount(Long accountId) {
            return new ArrayList<>(savedTransactions);
        }

        @Override
        public Long createRecurringTemplate(RecurringSuggestion suggestion, Long accountId, LocalDate nextDueDate) {
            return 200L;
        }

        @Override
        public void linkTransactionsToTemplate(List<Long> transactionIds, Long templateId) {
        }

        @Override
        public void notifyBudgetDataUpdated() {
            notifyCalls++;
        }
    }
}
