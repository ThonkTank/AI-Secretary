package com.autosecretary.ui.update;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.autosecretary.presentation.databinding.FragmentUpdatePanelBinding;
import com.autosecretary.ui.FeatureHost;
import com.autosecretary.ui.FeatureViewModels;

/** Update feature that renders state and forwards only newly consumed effects. */
public final class UpdatePanelFragment extends Fragment {
    private FragmentUpdatePanelBinding binding;
    private UpdatePanelController controller;

    @Nullable @Override public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle state) {
        binding = FragmentUpdatePanelBinding.inflate(inflater, container, false);
        UpdateViewModel model = FeatureViewModels.update(this);
        FeatureHost host = (FeatureHost) requireActivity();
        controller = new UpdatePanelController(binding, model, host.updatesEnabled(),
                host::canInstallPackages, host::handleUpdateEffect);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        controller.bind(getViewLifecycleOwner());
    }

    @Override public void onResume() {
        super.onResume();
        if (controller != null) controller.onResume();
    }

    @Override public void onDestroyView() {
        controller = null;
        binding = null;
        super.onDestroyView();
    }
}
