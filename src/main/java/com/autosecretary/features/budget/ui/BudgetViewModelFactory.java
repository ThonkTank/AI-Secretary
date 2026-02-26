package com.autosecretary.features.budget.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.budget.application.AmountParser;
import com.autosecretary.features.budget.application.BudgetSeedService;
import com.autosecretary.features.budget.application.importing.ApplyRecurringSuggestionsUseCase;
import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.application.importing.StatementFileParser;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.CalculateFreeBudgetUseCase;
import com.autosecretary.features.budget.domain.AccountBalanceTimelineService;
import com.autosecretary.features.budget.ui.internal.BudgetChartStateMapper;
import com.autosecretary.features.budget.ui.internal.BudgetOverviewLoader;
import com.autosecretary.features.budget.ui.internal.BudgetSummaryPresentationMapper;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class BudgetViewModelFactory implements ViewModelProvider.Factory {

    private static final DateTimeFormatter DAILY_POINT_LABEL =
            DateTimeFormatter.ofPattern("dd.MM", Locale.GERMAN);
    private static final DateTimeFormatter MONTHLY_POINT_LABEL =
            DateTimeFormatter.ofPattern("MMM yy", Locale.GERMAN);

    private final BudgetRepository repository;
    private final StatementFileParser parser;
    private final ExecutorService executor;
    private final Consumer<Runnable> postToMain;
    private final BudgetImportUseCase importUseCase;
    private final ApplyRecurringSuggestionsUseCase applyRecurringUseCase;
    private final CalculateFreeBudgetUseCase calculateFreeBudgetUseCase;

    public BudgetViewModelFactory(BudgetRepository repository,
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
        this.calculateFreeBudgetUseCase = calculateFreeBudgetUseCase;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(BudgetViewModel.class)) {
            BudgetChartStateMapper chartStateMapper = new BudgetChartStateMapper(
                    DAILY_POINT_LABEL,
                    MONTHLY_POINT_LABEL);
            BudgetSummaryPresentationMapper summaryPresentationMapper = new BudgetSummaryPresentationMapper();
            BudgetOverviewLoader overviewLoader = new BudgetOverviewLoader(
                    repository,
                    calculateFreeBudgetUseCase,
                    summaryPresentationMapper,
                    new AccountBalanceTimelineService(),
                    chartStateMapper);
            return modelClass.cast(new BudgetViewModel(
                    repository, parser, executor, postToMain,
                    importUseCase,
                    applyRecurringUseCase,
                    new BudgetSeedService(repository),
                    overviewLoader,
                    new AmountParser()));
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
