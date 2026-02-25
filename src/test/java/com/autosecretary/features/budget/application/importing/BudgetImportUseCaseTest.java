package com.autosecretary.features.budget.application.importing;

import com.autosecretary.features.budget.data.BudgetTransactionEntity;
import com.autosecretary.features.budget.domain.RecurringSuggestion;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BudgetImportUseCaseTest {

    @Test
    public void runImportPipeline_successfulImport() {
        FakeRepo repo = new FakeRepo();
        BudgetImportUseCase useCase = new BudgetImportUseCase(repo, new StatementFileParser(), Executors.newSingleThreadExecutor());

        String csv = "date,amountCents,payee,description,categoryId,importHash\n"
                + "2025-01-05,-1000,REWE,Food,10,hash_a\n"
                + "2025-01-06,1200,ACME,Salary,,hash_b\n";

        BudgetImportUseCase.ImportPipelineResult result = useCase.runImportPipeline(
                11L,
                "statement.csv",
                csv.getBytes(StandardCharsets.UTF_8),
                "text/csv"
        );

        Assert.assertEquals(2, result.totalTransactions());
        Assert.assertEquals(2, result.newTransactions());
        Assert.assertEquals(0, result.duplicates());
        Assert.assertEquals(1, result.autoCategorized());
        Assert.assertEquals(LocalDate.of(2025, 1, 5), result.periodStart());
        Assert.assertEquals(LocalDate.of(2025, 1, 6), result.periodEnd());
        Assert.assertNotNull(result.recurringSuggestions());

        Assert.assertEquals(2, repo.savedTransactions.size());
        Assert.assertEquals(1, repo.markCompletedCalls);
        Assert.assertEquals(1, repo.notifyCalls);
    }

    @Test
    public void runImportPipeline_duplicateCase() {
        FakeRepo repo = new FakeRepo();
        repo.existingImportHashes.add("dup_hash");
        BudgetImportUseCase useCase = new BudgetImportUseCase(repo, new StatementFileParser(), Executors.newSingleThreadExecutor());

        String csv = "date,amountCents,payee,description,categoryId,importHash\n"
                + "2025-01-05,-1000,REWE,Food,,dup_hash\n"
                + "2025-01-06,-1200,REWE,Food,,unique_hash\n";

        BudgetImportUseCase.ImportPipelineResult result = useCase.runImportPipeline(
                11L,
                "statement.csv",
                csv.getBytes(StandardCharsets.UTF_8),
                "text/csv"
        );

        Assert.assertEquals(2, result.totalTransactions());
        Assert.assertEquals(1, result.newTransactions());
        Assert.assertEquals(1, result.duplicates());
        Assert.assertEquals(1, repo.savedTransactions.size());
        Assert.assertEquals(1, repo.markCompletedCalls);
    }

    @Test
    public void executeAsync_marksImportFailedOnTechnicalError() throws Exception {
        FakeRepo repo = new FakeRepo();
        repo.throwOnSave = true;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        BudgetImportUseCase useCase = new BudgetImportUseCase(repo, new StatementFileParser(), executor);

        String csv = "date,amountCents,payee,description,categoryId,importHash\n"
                + "2025-01-05,-1000,REWE,Food,,hash_1\n";

        AtomicReference<String> errorRef = new AtomicReference<>();

        useCase.executeAsync(11L, "statement.csv", csv.getBytes(StandardCharsets.UTF_8), "text/csv", new BudgetImportUseCase.ImportCallback() {
            @Override
            public void onProgress(String message) {
            }

            @Override
            public void onSuccess(BudgetImportUseCase.ImportResult result) {
            }

            @Override
            public void onError(String errorMessage) {
                errorRef.set(errorMessage);
            }
        });

        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        Assert.assertEquals(1, repo.markFailedCalls);
        Assert.assertNotNull(errorRef.get());
        Assert.assertTrue(errorRef.get().startsWith("Technischer Fehler beim Import:"));
        Assert.assertEquals(repo.lastFailedMessage, errorRef.get());
    }

    @Test
    public void runImportPipeline_noNewTransactions() {
        FakeRepo repo = new FakeRepo();
        repo.existingImportHashes.add("h1");
        repo.existingImportHashes.add("h2");

        BudgetImportUseCase useCase = new BudgetImportUseCase(repo, new StatementFileParser(), Executors.newSingleThreadExecutor());

        String csv = "date,amountCents,payee,description,categoryId,importHash\n"
                + "2025-01-05,-1000,REWE,Food,,h1\n"
                + "2025-01-06,-1200,REWE,Food,,h2\n";

        BudgetImportUseCase.ImportPipelineResult result = useCase.runImportPipeline(
                11L,
                "statement.csv",
                csv.getBytes(StandardCharsets.UTF_8),
                "text/csv"
        );

        Assert.assertEquals(0, result.newTransactions());
        Assert.assertEquals(2, result.duplicates());
        Assert.assertEquals(0, repo.saveBatchCalls);
        Assert.assertEquals(1, repo.markCompletedCalls);
        Assert.assertEquals(1, repo.notifyCalls);
    }

    static class FakeRepo implements BudgetImportRepository {
        final List<String> existingImportHashes = new ArrayList<>();
        final List<BudgetTransactionEntity> savedTransactions = new ArrayList<>();

        int notifyCalls;
        int markCompletedCalls;
        int markFailedCalls;
        int saveBatchCalls;

        boolean throwOnSave;
        String lastFailedMessage;

        @Override
        public ImportRecord createImport(Long accountId, String fileName, String fileHash) {
            return new ImportRecord(99L, accountId, fileName, fileHash,
                    null, null, 0, 0, 0, "PENDING", null);
        }

        @Override
        public void markImportCompleted(Long importId, int totalTransactions, int importedTransactions, int autoCategorized,
                                        LocalDate periodStart, LocalDate periodEnd) {
            markCompletedCalls++;
        }

        @Override
        public void markImportFailed(Long importId, String errorMessage) {
            markFailedCalls++;
            lastFailedMessage = errorMessage;
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
            if (throwOnSave) {
                throw new RuntimeException("DB offline");
            }
            saveBatchCalls++;
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
