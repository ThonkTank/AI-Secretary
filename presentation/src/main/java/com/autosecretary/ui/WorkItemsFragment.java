package com.autosecretary.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.autosecretary.application.GetTodayTimeline;
import com.autosecretary.application.MoveWorkItemUseCase;
import com.autosecretary.presentation.R;
import com.autosecretary.presentation.databinding.FragmentWorkItemsBinding;
import com.autosecretary.ui.editor.AddWorkItemDialogFragment;
import com.autosecretary.ui.editor.ObligationEditorDialogFragment;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/** Work-item feature with typed filter and port-only actions. */
public final class WorkItemsFragment extends Fragment {
    private FragmentWorkItemsBinding binding;
    private MainViewModel viewModel;
    private WorkItemsPanelController panel;

    @Nullable @Override public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle state) {
        binding = FragmentWorkItemsBinding.inflate(inflater, container, false);
        viewModel = FeatureViewModels.main(this);
        panel = new WorkItemsPanelController(binding, host().timeProvider(),
                new WorkItemsPanelController.Actions() {
                    @Override public void selectFilter(WorkItemFilter filter) {
                        viewModel.selectFilter(filter);
                    }
                    @Override public void complete(String id) { viewModel.complete(id); }
                    @Override public void move(
                            String id, MoveWorkItemUseCase.Direction direction) {
                        viewModel.move(id, direction);
                    }
                    @Override public void edit(String id, boolean routine) {
                        var existing = viewModel.findWorkItem(id);
                        FeatureViewModels.editor(WorkItemsFragment.this).open(routine, existing);
                        showEditor();
                    }
                    @Override public void omitToday(String id) { viewModel.omitToday(id); }
                    @Override public void confirmDelete(WorkItemRow item) { confirmDelete(item); }
                    @Override public void confirmCleanup(List<String> ids, String message) {
                        showDeleteConfirmation("Erledigtes aufräumen", message, ids);
                    }
                    @Override public void undo() { viewModel.undo(); }
                    @Override public void deleteAll(List<String> ids) { viewModel.deleteAll(ids); }
                });
        binding.AddFab.setOnClickListener(view -> showAdd());
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        getParentFragmentManager().setFragmentResultListener(
                DeleteConfirmationDialogFragment.RESULT,
                getViewLifecycleOwner(),
                (key, result) -> {
                    List<String> ids = result.getStringArrayList(
                            DeleteConfirmationDialogFragment.IDS);
                    if (ids == null || ids.isEmpty()) return;
                    if (ids.size() == 1) viewModel.delete(ids.get(0));
                    else viewModel.deleteAll(ids);
                });
        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.effects().observe(getViewLifecycleOwner(), this::handleEffect);
    }

    @Override public void onResume() {
        super.onResume();
        viewModel.reload();
    }

    @Override public void onDestroyView() {
        panel = null;
        binding = null;
        super.onDestroyView();
    }

    private void render(MainUiState state) {
        if (binding == null || !(state instanceof MainUiState.Ready ready)) return;
        var timeline = new GetTodayTimeline(host().timeProvider()).execute(ready.dashboard());
        Dashboard dashboard = UiModelMapper.dashboard(ready.dashboard(), timeline,
                host().timeProvider().zone(), getString(R.string.calendar_private));
        panel.render(dashboard, ready.filter());
    }

    private void handleEffect(MainUiEffect effect) {
        if (effect == null) return;
        viewModel.consumeEffect(effect.id());
        if (effect instanceof MainUiEffect.Completion) {
            Snackbar.make(binding.Root, R.string.done, Snackbar.LENGTH_SHORT).show();
        } else if (effect instanceof MainUiEffect.Error error) {
            Toast.makeText(requireContext(), error.message(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDelete(WorkItemRow item) {
        String detail = item.routine()
                ? "„" + item.title() + "“ mit Schritten und Jahresring löschen?"
                : "„" + item.title() + "“ wirklich löschen?";
        showDeleteConfirmation("Eintrag löschen", detail, List.of(item.id()));
    }

    private void showDeleteConfirmation(String title, String message, List<String> ids) {
        if (getParentFragmentManager().findFragmentByTag(
                DeleteConfirmationDialogFragment.TAG) == null) {
            DeleteConfirmationDialogFragment.create(title, message, ids).show(
                    getParentFragmentManager(), DeleteConfirmationDialogFragment.TAG);
        }
    }

    private void showAdd() {
        if (getParentFragmentManager().findFragmentByTag(AddWorkItemDialogFragment.TAG) == null) {
            new AddWorkItemDialogFragment().show(
                    getParentFragmentManager(), AddWorkItemDialogFragment.TAG);
        }
    }

    private void showEditor() {
        if (getParentFragmentManager().findFragmentByTag(
                ObligationEditorDialogFragment.TAG) == null) {
            new ObligationEditorDialogFragment().show(
                    getParentFragmentManager(), ObligationEditorDialogFragment.TAG);
        }
    }

    private FeatureHost host() { return (FeatureHost) requireActivity(); }
}
