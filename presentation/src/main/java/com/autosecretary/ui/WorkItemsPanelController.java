package com.autosecretary.ui;

import android.graphics.Paint;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.autosecretary.application.MoveWorkItemUseCase;
import com.autosecretary.application.TimeProvider;
import com.autosecretary.presentation.R;
import com.autosecretary.presentation.databinding.ActivityMainBinding;
import com.google.android.material.snackbar.Snackbar;

import java.time.LocalDate;
import java.util.List;

/** Complete-list feature boundary: filtering, list rendering, cleanup and row actions. */
public final class WorkItemsPanelController {
    public interface Actions {
        void selectFilter(WorkItemFilter filter);
        void complete(String id);
        void move(String id, MoveWorkItemUseCase.Direction direction);
        void edit(String id, boolean routine);
        void omitToday(String id);
        void confirmDelete(WorkItemRow item);
        void undo();
        void deleteAll(List<String> ids);
    }

    private final ActivityMainBinding binding;
    private final TimeProvider time;
    private final Actions actions;
    private final ObligationAdapter adapter;
    private Dashboard dashboard = new Dashboard(List.of(), List.of());

    public WorkItemsPanelController(
            ActivityMainBinding binding, TimeProvider time, Actions actions) {
        this.binding = binding;
        this.time = time;
        this.actions = actions;
        adapter = new ObligationAdapter(new ObligationAdapter.Listener() {
            @Override public void onComplete(String id) { actions.complete(id); }
            @Override public void onMove(String id, MoveWorkItemUseCase.Direction direction) {
                actions.move(id, direction);
                Snackbar.make(binding.Root, "Reihenfolge geändert", Snackbar.LENGTH_LONG)
                        .setAction("zurücknehmen", view -> actions.undo()).show();
            }
            @Override public void onEdit(String id, boolean routine) {
                actions.edit(id, routine);
            }
            @Override public void onOmit(String id) { actions.omitToday(id); }
            @Override public void onDelete(WorkItemRow item) { actions.confirmDelete(item); }
        });
        binding.ObligationList.setLayoutManager(new LinearLayoutManager(binding.Root.getContext()));
        binding.ObligationList.setAdapter(adapter);
        binding.ObligationList.setNestedScrollingEnabled(false);
        binding.FilterOpen.setOnClickListener(view -> actions.selectFilter(WorkItemFilter.OPEN));
        binding.FilterRoutines.setOnClickListener(
                view -> actions.selectFilter(WorkItemFilter.ROUTINES));
        binding.FilterDone.setOnClickListener(view -> actions.selectFilter(WorkItemFilter.DONE));
        binding.CompletedCleanupAction.setOnClickListener(view -> confirmCleanup());
    }

    public void render(Dashboard dashboard, WorkItemFilter filter) {
        this.dashboard = dashboard;
        setSelected(binding.FilterOpen, filter == WorkItemFilter.OPEN);
        setSelected(binding.FilterRoutines, filter == WorkItemFilter.ROUTINES);
        setSelected(binding.FilterDone, filter == WorkItemFilter.DONE);
        List<WorkItemRow> filtered = dashboard.workItems().stream()
                .filter(item -> switch (filter) {
                    case ROUTINES -> item.routine();
                    case DONE -> !item.routine() && item.completed();
                    case OPEN -> item.open();
                })
                .sorted(java.util.Comparator.comparingInt(
                        item -> groupPriority(item.group())))
                .collect(java.util.stream.Collectors.toList());
        adapter.submit(filtered);
        boolean completed = filter == WorkItemFilter.DONE;
        List<String> cleanupIds = completedCleanupIds();
        binding.CompletedCleanup.setVisibility(completed ? View.VISIBLE : View.GONE);
        binding.CompletedCleanupAction.setEnabled(!cleanupIds.isEmpty());
        binding.CompletedCleanupAction.setAlpha(cleanupIds.isEmpty() ? .45f : 1f);
        String title = "Alles · " + filtered.size();
        android.text.SpannableString styled = new android.text.SpannableString(title);
        styled.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                6, title.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.AllHeading.setText(styled);
    }

    public void setEvening(boolean evening) { adapter.setEvening(evening); }

    private void setSelected(TextView view, boolean selected) {
        view.setTextColor(ContextCompat.getColor(view.getContext(),
                selected ? R.color.forest : R.color.marker));
        view.setPaintFlags(selected
                ? view.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG
                : view.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
    }

    private List<String> completedCleanupIds() {
        LocalDate cutoff = time.localNow().toLocalDate().minusDays(30);
        return dashboard.workItems().stream()
                .filter(WorkItemRow::completed)
                .filter(item -> item.completedAt() != null && item.completedAt().isBefore(cutoff))
                .map(WorkItemRow::id).collect(java.util.stream.Collectors.toList());
    }

    private void confirmCleanup() {
        List<String> ids = completedCleanupIds();
        if (ids.isEmpty()) return;
        String count = ids.size() == 1 ? "ein erledigtes Blatt"
                : ids.size() + " erledigte Blätter";
        AlertDialog dialog = new AlertDialog.Builder(binding.Root.getContext())
                .setTitle("Erledigtes aufräumen")
                .setMessage(count + " ist älter als 30 Tage. Wirklich löschen?")
                .setPositiveButton("Löschen", (ignored, which) -> actions.deleteAll(ids))
                .setNegativeButton("behalten", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(binding.Root.getContext(), R.color.danger));
    }

    private static int groupPriority(String group) {
        return switch (group) {
            case "überfällig" -> 0;
            case "heute", "heute fällig", "heute erledigt" -> 1;
            case "diese Woche", "gestern" -> 2;
            case "ohne Termin", "seltener", "älter" -> 3;
            default -> 4;
        };
    }
}
