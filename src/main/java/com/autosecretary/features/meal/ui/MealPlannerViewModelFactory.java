package com.autosecretary.features.meal.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.meal.application.MealPlannerPresenter;

/**
 * Factory for {@link MealPlannerViewModel}.
 */
public class MealPlannerViewModelFactory implements ViewModelProvider.Factory {
    private final MealPlannerPresenter presenter;

    public MealPlannerViewModelFactory(MealPlannerPresenter presenter) {
        this.presenter = presenter;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(MealPlannerViewModel.class)) {
            return modelClass.cast(new MealPlannerViewModel(presenter));
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
