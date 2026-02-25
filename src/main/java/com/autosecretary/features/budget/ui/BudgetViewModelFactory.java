package com.autosecretary.features.budget.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.budget.application.importing.StatementFileParser;
import com.autosecretary.features.budget.domain.BudgetRepository;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class BudgetViewModelFactory implements ViewModelProvider.Factory {

    private final BudgetRepository repository;
    private final StatementFileParser parser;
    private final ExecutorService executor;
    private final Consumer<Runnable> postToMain;

    public BudgetViewModelFactory(BudgetRepository repository,
                                  StatementFileParser parser,
                                  ExecutorService executor,
                                  Consumer<Runnable> postToMain) {
        this.repository = repository;
        this.parser = parser;
        this.executor = executor;
        this.postToMain = postToMain;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(BudgetViewModel.class)) {
            return modelClass.cast(new BudgetViewModel(repository, parser, executor, postToMain));
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
