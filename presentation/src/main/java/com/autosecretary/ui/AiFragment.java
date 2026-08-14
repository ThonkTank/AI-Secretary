package com.autosecretary.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.autosecretary.presentation.databinding.FragmentAiBinding;
import com.autosecretary.ui.ai.AiPanelController;
import com.autosecretary.ui.settings.PlanningSettingsDialogFragment;

/** Consent, model transfer, inference and proposal feature. */
public final class AiFragment extends Fragment {
    private FragmentAiBinding binding;
    private AiPanelController controller;

    @Nullable @Override public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle state) {
        binding = FragmentAiBinding.inflate(inflater, container, false);
        var ai = FeatureViewModels.ai(this);
        var planning = FeatureViewModels.planningSettings(this);
        controller = new AiPanelController(
                (androidx.appcompat.app.AppCompatActivity) requireActivity(),
                binding, ai, this::showError);
        binding.PlanningSettings.setOnClickListener(view -> {
            planning.open();
            if (getParentFragmentManager().findFragmentByTag(
                    PlanningSettingsDialogFragment.TAG) == null) {
                new PlanningSettingsDialogFragment().show(
                        getParentFragmentManager(), PlanningSettingsDialogFragment.TAG);
            }
        });
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        controller.bind(getViewLifecycleOwner());
    }

    @Override public void onDestroyView() {
        binding = null;
        controller = null;
        super.onDestroyView();
    }

    private void showError(String message) {
        if (getParentFragmentManager().findFragmentByTag(ErrorDialogFragment.TAG) == null) {
            ErrorDialogFragment.create(message).show(
                    getParentFragmentManager(), ErrorDialogFragment.TAG);
        }
    }
}
