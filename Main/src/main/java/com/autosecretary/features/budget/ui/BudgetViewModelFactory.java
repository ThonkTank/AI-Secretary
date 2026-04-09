package com.autosecretary.features.budget.ui;

import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.budget.application.importing.ApplyRecurringSuggestionsUseCase;
import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.application.BudgetSeedService;
import com.autosecretary.features.budget.application.CalculateEffectiveBudgetLimitUseCase;
import com.autosecretary.features.budget.application.CreateTransferUseCase;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.ui.internal.BudgetOverviewLoader;

import java.util.concurrent.ExecutorService;

/**
 * {@link ViewModelProvider.Factory} for {@link BudgetViewModel}.
 *
 * <p>Android's {@code ViewModelProvider} requires a factory when the ViewModel has a
 * non-default constructor. This factory is created by {@code AppCompositionRoot} and
 * receives the shared infrastructure dependencies ({@code repository}, {@code executor})
 * that are managed at the app level.
 *
 * <p>Pure computation helpers ({@link BudgetSummaryPresentationMapper},
 * {@link BudgetOverviewLoader}, {@link CalculateEffectiveBudgetLimitUseCase},
 * {@link BudgetSeedService}) are constructed here inside {@link #create} rather than
 * injected from outside: they are stateless, cheap to construct, and tying them to the
 * factory's constructor would expose implementation details of the ViewModel to the DI root.
 */
public class BudgetViewModelFactory implements ViewModelProvider.Factory {

    private final BudgetRepository repository;
    private final ExecutorService executor;
    private final BudgetImportUseCase importUseCase;
    private final ApplyRecurringSuggestionsUseCase applyRecurringUseCase;
    private final CreateTransferUseCase createTransferUseCase;
    private final Resources resources;

    public BudgetViewModelFactory(BudgetRepository repository,
                                  ExecutorService executor,
                                  BudgetImportUseCase importUseCase,
                                  ApplyRecurringSuggestionsUseCase applyRecurringUseCase,
                                  CreateTransferUseCase createTransferUseCase,
                                  Resources resources) {
        this.repository = repository;
        this.executor = executor;
        this.importUseCase = importUseCase;
        this.applyRecurringUseCase = applyRecurringUseCase;
        this.createTransferUseCase = createTransferUseCase;
        this.resources = resources;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(BudgetViewModel.class)) {
            BudgetOverviewLoader overviewLoader = new BudgetOverviewLoader(
                    repository,
                    resources);
            return modelClass.cast(new BudgetViewModel(
                    repository, executor,
                    importUseCase,
                    applyRecurringUseCase,
                    createTransferUseCase,
                    new CalculateEffectiveBudgetLimitUseCase(repository),
                    overviewLoader,
                    new BudgetSeedService(repository)));
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
