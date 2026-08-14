package com.autosecretary.ui;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.ui.ai.AiViewModel;

public final class FeatureViewModels {
    private FeatureViewModels() { }

    public static MainViewModel main(Fragment fragment) {
        return get(fragment, MainViewModel.class);
    }

    public static AiViewModel ai(Fragment fragment) {
        return get(fragment, AiViewModel.class);
    }

    private static <T extends ViewModel> T get(Fragment fragment, Class<T> type) {
        var activity = fragment.requireActivity();
        var owner = (FeatureViewModelFactoryOwner) activity;
        return new ViewModelProvider(activity, owner.featureViewModelFactory()).get(type);
    }
}
