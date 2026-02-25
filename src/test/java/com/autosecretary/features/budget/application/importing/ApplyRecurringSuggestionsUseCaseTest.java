package com.autosecretary.features.budget.application.importing;

import com.autosecretary.features.budget.data.BudgetTransactionEntity;
import com.autosecretary.features.budget.domain.RecurringBudgetTransaction;
import com.autosecretary.features.budget.domain.RecurringSuggestion;

import org.junit.Assert;
import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ApplyRecurringSuggestionsUseCaseTest {

    @Test
    public void executeAsync_createsTemplateAndLinksTransactions() throws Exception {
        FakeRepo repo = new FakeRepo();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ApplyRecurringSuggestionsUseCase useCase = new ApplyRecurringSuggestionsUseCase(repo, executor);

        RecurringSuggestion suggestion = new RecurringSuggestion(
                "NETFLIX",
                "Netflix",
                5L,
                -1299,
                -1299,
                -1299,
                RecurringBudgetTransaction.RecurringType.MONTHLY_DAY,
                1,
                DayOfWeek.MONDAY,
                List.of(10L, 11L, 12L),
                0.91
        );

        AtomicReference<String> errorRef = new AtomicReference<>();
        AtomicReference<Boolean> done = new AtomicReference<>(false);

        useCase.executeAsync(77L, List.of(suggestion), () -> done.set(true), errorRef::set);

        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        Assert.assertTrue(done.get());
        Assert.assertNull(errorRef.get());

        Assert.assertEquals(1, repo.createdTemplateIds.size());
        Assert.assertEquals(Long.valueOf(300L), repo.createdTemplateIds.get(0));
        Assert.assertEquals(1, repo.linkedTransactionGroups.size());
        Assert.assertEquals(List.of(10L, 11L, 12L), repo.linkedTransactionGroups.get(0));
        Assert.assertEquals(1, repo.notifyCalls);
    }

    static class FakeRepo implements BudgetImportRepository {
        final List<Long> createdTemplateIds = new ArrayList<>();
        final List<List<Long>> linkedTransactionGroups = new ArrayList<>();
        int notifyCalls;

        @Override
        public ImportRecord createImport(Long accountId, String fileName, String fileHash) {
            return null;
        }

        @Override
        public void markImportCompleted(Long importId, int totalTransactions, int importedTransactions, int autoCategorized, LocalDate periodStart, LocalDate periodEnd) {
        }

        @Override
        public void markImportFailed(Long importId, String errorMessage) {
        }

        @Override
        public boolean existsTransactionByImportHash(String importHash) {
            return false;
        }

        @Override
        public Long findDefaultCategoryId(boolean income) {
            return null;
        }

        @Override
        public void saveTransactionsBatch(List<BudgetTransactionEntity> transactions) {
        }

        @Override
        public List<BudgetTransactionEntity> loadTransactionsForAccount(Long accountId) {
            return List.of();
        }

        @Override
        public Long createRecurringTemplate(RecurringSuggestion suggestion, Long accountId, LocalDate nextDueDate) {
            createdTemplateIds.add(300L);
            return 300L;
        }

        @Override
        public void linkTransactionsToTemplate(List<Long> transactionIds, Long templateId) {
            linkedTransactionGroups.add(new ArrayList<>(transactionIds));
        }

        @Override
        public void notifyBudgetDataUpdated() {
            notifyCalls++;
        }
    }
}
