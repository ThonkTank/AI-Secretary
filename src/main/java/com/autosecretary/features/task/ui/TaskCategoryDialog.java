package com.autosecretary.features.task.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.R;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.task.domain.model.TaskCategory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dialog for managing task categories: create, rename, re-icon/re-colour, and delete.
 *
 * <p>Categories are the flat grouping that replaced the former parent-child task hierarchy.
 * Phase 1 introduced them (selection dropdown + migration from old parents) but provided no
 * way to create or edit them; this dialog fills that gap. Rows are inflated programmatically
 * into {@code CategoryManageContainer}; edits mutate in-memory draft objects live via text
 * watchers, and the whole set is persisted atomically on save. Deleting a persisted category
 * clears its assignment from any task (emulating ON DELETE SET NULL).
 */
public class TaskCategoryDialog extends DialogFragment {
    public static final String TAG = "category_manage";

    private TaskCategoryViewModel viewModel;
    private LinearLayout container;
    private View contentView;
    private View loadingView;

    /** Live draft categories, mutated in place by row text watchers; written to DB on save. */
    private final List<TaskCategory> drafts = new ArrayList<>();
    /** Ids of categories that existed on load, used to detect deletions. */
    private final Set<String> originalIds = new HashSet<>();
    /** Ids removed by the user that were originally persisted (need DB deletion). */
    private final List<String> deletedIds = new ArrayList<>();

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (viewModel == null) {
            TaskCategoryViewModelFactory factory = AutoSecretaryApplication.from(requireContext())
                    .getTaskCategoryViewModelFactory();
            viewModel = new ViewModelProvider(this, factory).get(TaskCategoryViewModel.class);
        }

        View root = LayoutInflater.from(requireContext())
                .inflate(R.layout.task_category_manage_dialog, null, false);
        container = root.findViewById(R.id.CategoryManageContainer);
        contentView = root.findViewById(R.id.CategoryManageContent);
        loadingView = root.findViewById(R.id.CategoryManageLoading);
        root.findViewById(R.id.CategoryManageAddButton).setOnClickListener(v -> addCategory());

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.task_category_dialog_title)
                .setView(root)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        loadCategories();
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) {
            return;
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> save());
    }

    private void loadCategories() {
        loadingView.setVisibility(View.VISIBLE);
        contentView.setVisibility(View.GONE);
        viewModel.loadCategories(categories -> {
            drafts.clear();
            originalIds.clear();
            deletedIds.clear();
            for (TaskCategory category : categories) {
                drafts.add(category);
                originalIds.add(category.id);
            }
            loadingView.setVisibility(View.GONE);
            contentView.setVisibility(View.VISIBLE);
            renderRows();
        });
    }

    private void addCategory() {
        TaskCategory category = new TaskCategory();
        category.name = "";
        drafts.add(category);
        renderRows();
    }

    private void renderRows() {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (TaskCategory category : drafts) {
            View row = inflater.inflate(R.layout.task_category_item, container, false);
            EditText iconView = row.findViewById(R.id.CategoryItemIcon);
            EditText nameView = row.findViewById(R.id.CategoryItemName);
            EditText colorView = row.findViewById(R.id.CategoryItemColor);
            ImageButton deleteButton = row.findViewById(R.id.CategoryItemDelete);

            iconView.setText(category.icon);
            nameView.setText(category.name);
            colorView.setText(category.colorHex);

            iconView.addTextChangedListener(new FieldWatcher(text -> category.icon = text));
            nameView.addTextChangedListener(new FieldWatcher(text -> category.name = text));
            colorView.addTextChangedListener(new FieldWatcher(text -> category.colorHex = text));

            deleteButton.setOnClickListener(v -> {
                drafts.remove(category);
                if (originalIds.contains(category.id)) {
                    deletedIds.add(category.id);
                }
                renderRows();
            });

            container.addView(row);
        }
    }

    private void save() {
        List<TaskCategory> valid = new ArrayList<>();
        for (TaskCategory category : drafts) {
            String name = category.name == null ? "" : category.name.trim();
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), R.string.task_category_name_required,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            category.name = name;
            category.icon = normalizeIcon(category.icon);
            category.colorHex = normalizeColor(category.colorHex);
            valid.add(category);
        }
        viewModel.saveCategories(valid, new ArrayList<>(deletedIds), () -> {
            Toast.makeText(requireContext(), R.string.task_category_saved, Toast.LENGTH_SHORT).show();
            dismiss();
        });
    }

    private String normalizeIcon(String icon) {
        if (icon == null || icon.trim().isEmpty()) {
            return com.autosecretary.features.task.domain.model.TaskCore.DEFAULT_GOAL_ICON;
        }
        return icon.trim();
    }

    private String normalizeColor(String color) {
        String trimmed = color == null ? "" : color.trim();
        if (trimmed.matches("#[0-9a-fA-F]{6}")) {
            return trimmed;
        }
        return com.autosecretary.features.task.domain.model.TaskCore.DEFAULT_GOAL_COLOR_HEX;
    }

    /** Minimal {@link TextWatcher} that forwards the current text to a field setter. */
    private static final class FieldWatcher implements TextWatcher {
        private final java.util.function.Consumer<String> setter;

        FieldWatcher(java.util.function.Consumer<String> setter) {
            this.setter = setter;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            setter.accept(s.toString());
        }
    }
}
