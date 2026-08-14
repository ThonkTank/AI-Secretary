package com.autosecretary.ui;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.ui.ai.AiViewModel;
import com.autosecretary.ui.editor.EditorViewModel;
import com.autosecretary.ui.settings.PlanningSettingsViewModel;
import com.autosecretary.ui.update.UpdateViewModel;

public final class FeatureViewModels {
    private FeatureViewModels() { }

    public static MainViewModel main(Fragment fragment) {
        return get(fragment, MainViewModel.class);
    }

    public static AiViewModel ai(Fragment fragment) {
        return get(fragment, AiViewModel.class);
    }

    public static EditorViewModel editor(Fragment fragment) {
        return get(fragment, EditorViewModel.class);
    }

    public static PlanningSettingsViewModel planningSettings(Fragment fragment) {
        return get(fragment, PlanningSettingsViewModel.class);
    }

    public static UpdateViewModel update(Fragment fragment) {
        return get(fragment, UpdateViewModel.class);
    }

    private static <T extends ViewModel> T get(Fragment fragment, Class<T> type) {
        var activity = fragment.requireActivity();
        var owner = (FeatureViewModelFactoryOwner) activity;
        return new ViewModelProvider(activity, owner.featureViewModelFactory()).get(type);
    }
}
