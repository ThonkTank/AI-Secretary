package com.autosecretary.features.budget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.budget.application.BudgetSeedService;
import com.autosecretary.features.budget.application.BudgetTransactionMutationUseCase;
import com.autosecretary.features.budget.application.CalculateEffectiveBudgetLimitUseCase;
import com.autosecretary.features.budget.application.CreateTransferUseCase;
import com.autosecretary.features.budget.application.LoadBudgetLimitOverviewUseCase;
import com.autosecretary.features.budget.application.LoadBudgetOverviewUseCase;
import com.autosecretary.features.budget.application.ResolveBudgetAccountUseCase;
import com.autosecretary.features.budget.application.importing.ApplyRecurringSuggestionsUseCase;
import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.application.importing.internal.StatementFileParser;
import com.autosecretary.features.budget.data.repository.BudgetImportRoomRepository;
import com.autosecretary.features.budget.data.repository.BudgetRoomRepository;
import com.autosecretary.features.budget.domain.BudgetAccount;
import com.autosecretary.features.budget.domain.BudgetCategory;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.ui.BudgetViewModel;
import com.autosecretary.features.budget.ui.state.BudgetLimitBar;
import com.autosecretary.features.budget.application.overview.BudgetSummaryData;
import com.autosecretary.features.budget.application.overview.BudgetTransactionRow;
import com.autosecretary.features.budget.ui.state.BudgetUiState;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.BudgetFixtures;
import com.autosecretary.testing.LiveDataTestUtil;
import com.autosecretary.testing.RobolectricDrain;
import com.autosecretary.testing.SynchronousExecutorService;
import com.autosecretary.testing.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

public final class BudgetOverviewCharacterizationTest extends AutoSecretaryRobolectricTest {
    @Rule
    public final InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase db;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void budgetOverviewViewModelKeepsSelectedAccountContentSummaryRowsAndChartInvariant() {
        BudgetRoomRepository repository = BudgetFixtures.budgetRepository(db);
        repository.insertAccount(BudgetFixtures.account("Haushalt"));
        repository.insertCategory(BudgetFixtures.expenseCategory("Lebensmittel"));

        BudgetAccount account = repository.findActiveAccounts().get(0);
        BudgetCategory category = repository.findActiveCategories().get(0);
        repository.saveTransaction(new BudgetRepository.TransactionCreateDetails(
                account.id(),
                category.id(),
                TransactionDirection.EXPENSE,
                1234L,
                LocalDate.now(),
                "Wocheneinkauf"));

        BudgetViewModel viewModel = createBudgetViewModel(repository);
        RobolectricDrain.mainLooper();

        assertEquals(BudgetUiState.CONTENT, LiveDataTestUtil.valueOf(viewModel.getUiState()));
        assertEquals(account.id(), LiveDataTestUtil.valueOf(viewModel.getSelectedAccountId()));

        BudgetSummaryData summary = LiveDataTestUtil.valueOf(viewModel.getSummaryData());
        assertNotNull(summary);
        assertEquals(1234L, summary.expenseCents());
        assertEquals(-1234L, summary.freeBudgetCents());

        List<BudgetTransactionRow> rows = LiveDataTestUtil.valueOf(viewModel.getTransactions());
        assertEquals(1, rows.size());
        assertEquals(1234L, rows.get(0).amountCents());
        assertTrue(rows.get(0).label().contains("Lebensmittel"));

        assertFalse(LiveDataTestUtil.valueOf(viewModel.getChartPoints()).isEmpty());
    }

    @Test
    public void budgetMutationAndLimitBarKeepOverviewRefreshInvariant() {
        BudgetRoomRepository repository = BudgetFixtures.budgetRepository(db);
        repository.insertAccount(BudgetFixtures.account("Haushalt"));
        repository.insertCategory(BudgetFixtures.expenseCategory("Lebensmittel"));

        BudgetAccount account = repository.findActiveAccounts().get(0);
        BudgetCategory category = repository.findActiveCategories().get(0);
        repository.saveTransaction(new BudgetRepository.TransactionCreateDetails(
                account.id(),
                category.id(),
                TransactionDirection.INCOME,
                5000L,
                LocalDate.now(),
                "Startsaldo"));
        BudgetViewModel viewModel = createBudgetViewModel(repository);
        RobolectricDrain.mainLooper();

        viewModel.addTransaction("12,34", true, category.id(), "Wocheneinkauf",
                LocalDate.now(), account.id());
        RobolectricDrain.mainLooper();

        BudgetSummaryData summary = LiveDataTestUtil.valueOf(viewModel.getSummaryData());
        assertNotNull(summary);
        assertEquals(5000L, summary.incomeCents());
        assertEquals(1234L, summary.expenseCents());
        assertEquals(3766L, summary.freeBudgetCents());

        List<BudgetTransactionRow> rows = LiveDataTestUtil.valueOf(viewModel.getTransactions());
        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(row -> "Wocheneinkauf".equals(row.note())));

        viewModel.saveBudgetLimitFromString(category.id(), "20,00", false, "");
        RobolectricDrain.mainLooper();

        List<BudgetLimitBar> limits = LiveDataTestUtil.valueOf(viewModel.getLimits());
        assertEquals(1, limits.size());
        assertEquals(category.id(), limits.get(0).categoryId());
        assertEquals(1234L, limits.get(0).spentCents());
        assertEquals(2000L, limits.get(0).baseLimitCents());
        assertEquals(61, limits.get(0).percentage());
    }

    private BudgetViewModel createBudgetViewModel(BudgetRoomRepository repository) {
        Context context = ApplicationProvider.getApplicationContext();
        BudgetImportRoomRepository importRepository = BudgetFixtures.budgetImportRepository(db);
        StatementFileParser parser = new StatementFileParser(null, null, importRepository);
        SynchronousExecutorService executor = new SynchronousExecutorService();

        return new BudgetViewModel(
                new BudgetViewModel.Infrastructure(executor),
                new BudgetViewModel.UseCases(
                        new BudgetImportUseCase(importRepository, parser),
                        new ApplyRecurringSuggestionsUseCase(importRepository),
                        new CreateTransferUseCase(repository),
                        new BudgetTransactionMutationUseCase(repository),
                        new ResolveBudgetAccountUseCase(repository),
                        new LoadBudgetLimitOverviewUseCase(repository,
                                new CalculateEffectiveBudgetLimitUseCase(repository)),
                        new BudgetSeedService(repository)),
                new BudgetViewModel.Presentation(
                        new LoadBudgetOverviewUseCase(repository, context.getResources())));
    }
}
