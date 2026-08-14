package com.autosecretary.ui;

import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.autosecretary.application.DashboardData;
import com.autosecretary.application.MoveWorkItemUseCase;
import com.autosecretary.application.TimeProvider;
import com.autosecretary.domain.PlanConflict;
import com.autosecretary.domain.WorkItem;
import com.autosecretary.presentation.R;
import com.autosecretary.presentation.databinding.FragmentTodayBinding;
import com.google.android.material.snackbar.Snackbar;

import java.time.LocalDate;
import java.util.List;

/** Today feature boundary: one canonical timeline, expansion, empty state and conflicts. */
public final class TodayPanelController {
    public interface Actions {
        void complete(String id);
        void setStepCompleted(String itemId, String stepId, boolean completed);
        void move(String id, MoveWorkItemUseCase.Direction direction);
        void omitToday(String id);
        void undo();
    }

    private final FragmentTodayBinding binding;
    private final TimeProvider time;
    private final Actions actions;
    private final TodayAdapter adapter;
    private final CompletedTodayAdapter completedAdapter;
    private Dashboard dashboard = new Dashboard(List.of(), List.of());
    private boolean expanded;

    public TodayPanelController(
            FragmentTodayBinding binding, TimeProvider time, boolean expanded, Actions actions) {
        this.binding = binding;
        this.time = time;
        this.expanded = expanded;
        this.actions = actions;
        adapter = new TodayAdapter(new TodayAdapter.Listener() {
            @Override public void onComplete(String id) { actions.complete(id); }
            @Override public void onStepChanged(
                    String id, String stepId, boolean completed) {
                actions.setStepCompleted(id, stepId, completed);
            }
            @Override public void onMove(String id, MoveWorkItemUseCase.Direction direction) {
                actions.move(id, direction);
                Snackbar.make(binding.Root, "Reihenfolge geändert", Snackbar.LENGTH_LONG)
                        .setAction("zurücknehmen", view -> actions.undo()).show();
            }
            @Override public void onOmit(String id) { actions.omitToday(id); }
        });
        binding.FocusList.setLayoutManager(new LinearLayoutManager(binding.Root.getContext()));
        binding.FocusList.setAdapter(adapter);
        binding.FocusList.setNestedScrollingEnabled(false);
        completedAdapter = new CompletedTodayAdapter();
        binding.CompletedToday.setLayoutManager(
                new LinearLayoutManager(binding.Root.getContext()));
        binding.CompletedToday.setAdapter(completedAdapter);
        binding.FocusMore.setOnClickListener(view -> {
            this.expanded = !this.expanded;
            submitRows();
        });
        binding.TodayUndoAction.setOnClickListener(view -> actions.undo());
    }

    public void render(
            Dashboard dashboard,
            DashboardData source,
            List<PlanConflict> conflicts,
            String undoLabel) {
        this.dashboard = dashboard;
        submitRows();
        renderEmpty(source);
        renderConflicts(conflicts);
        binding.TodayUndoRow.setVisibility(undoLabel != null
                && undoLabel.startsWith("Aus heute genommen") ? View.VISIBLE : View.GONE);
    }

    public boolean expanded() { return expanded; }

    public void setEvening(boolean evening) {
        adapter.setEvening(evening);
        binding.FocusStack.getChildAt(0).setBackgroundResource(evening
                ? R.drawable.bg_leaf_low_evening : R.drawable.bg_leaf_low);
        binding.FocusStack.getChildAt(1).setBackgroundResource(evening
                ? R.drawable.bg_leaf_middle_mirror_evening : R.drawable.bg_leaf_middle_mirror);
        binding.EmptyFocus.setBackgroundResource(evening
                ? R.drawable.bg_leaf_focus_evening : R.drawable.bg_leaf_focus);
        binding.FocusMore.setTextColor(evening ? 0xFFA08B62
                : ContextCompat.getColor(binding.Root.getContext(), R.color.marker));
    }

    private void renderConflicts(List<PlanConflict> conflicts) {
        if (conflicts.isEmpty()) {
            binding.PlanningConflicts.setVisibility(View.GONE);
            return;
        }
        StringBuilder message = new StringBuilder("heute kein passendes Zeitfenster");
        conflicts.stream().limit(3).forEach(conflict -> message.append("\n• ")
                .append(conflict.workItem().title()).append(" — ")
                .append(switch (conflict.reason()) {
                    case AFTER_DEADLINE -> "passt vorher in kein freies Fenster";
                    case NO_CAPACITY -> "sobald wieder genug Platz ist";
                    case OUTSIDE_HORIZON -> "wird später vorgeschlagen";
                }));
        if (conflicts.size() > 3) {
            message.append("\n• und ").append(conflicts.size() - 3).append(" weitere");
        }
        binding.PlanningConflicts.setText(message.toString());
        binding.PlanningConflicts.setVisibility(View.VISIBLE);
    }

    private void renderEmpty(DashboardData source) {
        boolean empty = dashboard.today().isEmpty();
        binding.EmptyFocus.setVisibility(empty ? View.VISIBLE : View.GONE);
        completedAdapter.submitList(List.of());
        binding.CompletedToday.setVisibility(View.GONE);
        if (!empty) return;

        LocalDate today = java.time.LocalDateTime.ofInstant(
                time.now(), time.zone()).toLocalDate();
        List<String> completedIds = source.completions().stream()
                .filter(value -> value.completedAt().toLocalDate().equals(today))
                .map(com.autosecretary.application.CompletionRecord::workItemId)
                .distinct().collect(java.util.stream.Collectors.toList());
        List<WorkItem> completedItems = source.workItems().stream()
                .filter(item -> completedIds.contains(item.id()))
                .collect(java.util.stream.Collectors.toList());
        if (!completedItems.isEmpty()) {
            int minutes = completedItems.stream().mapToInt(WorkItem::durationMinutes).sum();
            int weeks = completedItems.stream().mapToInt(
                    item -> item.stats().currentStreak()).max().orElse(1);
            binding.EmptyFocusMarker.setText("geschafft");
            binding.EmptyFocusTitle.setText("Heute ist alles erledigt.");
            binding.EmptyFocusDetail.setText(completedItems.size() == 1
                    ? "ein Blatt, ca. " + minutes + " Min"
                    : completedItems.size() + " Blätter, ca. " + minutes + " Min");
            binding.EmptyAnnualRing.setText(Integer.toString(Math.max(1, weeks)));
            binding.EmptyAnnualRing.setVisibility(View.VISIBLE);
            completedAdapter.submitList(completedItems.stream().limit(2)
                    .collect(java.util.stream.Collectors.toList()));
            binding.CompletedToday.setVisibility(View.VISIBLE);
        } else {
            binding.EmptyAnnualRing.setVisibility(View.GONE);
            binding.EmptyFocusMarker.setText("heute");
            if (!source.conflicts().isEmpty()) {
                binding.EmptyFocusTitle.setText("Heute passt kein Blatt ins Zeitfenster.");
                binding.EmptyFocusDetail.setText(
                        "Die offenen Aufgaben bleiben unter alles ansehen.");
            } else {
                binding.EmptyFocusTitle.setText(R.string.empty_focus);
                binding.EmptyFocusDetail.setText("Mit ＋ wächst eine neue Aufgabe.");
            }
        }
    }

    private void submitRows() {
        int additional = Math.max(0, dashboard.today().size() - 3);
        if (additional == 0) expanded = false;
        adapter.submit(TodayRowVisibility.visible(dashboard, expanded));
        boolean stackVisible = !expanded && additional > 0;
        binding.FocusStack.setVisibility(stackVisible ? View.VISIBLE : View.GONE);
        ViewGroup.MarginLayoutParams margins =
                (ViewGroup.MarginLayoutParams) binding.FocusList.getLayoutParams();
        int wantedTopMargin = stackVisible ? -dp(92) : 0;
        if (margins.topMargin != wantedTopMargin) {
            margins.topMargin = wantedTopMargin;
            binding.FocusList.setLayoutParams(margins);
        }
        binding.FocusMore.setVisibility(additional == 0 ? View.GONE : View.VISIBLE);
        binding.FocusMore.setText(expanded ? "wieder auf drei reduzieren"
                : additional == 1 ? "und ein weiterer heute · zeigen"
                : "und " + additional + " weitere heute · zeigen");
    }

    private int dp(int value) {
        return Math.round(value * binding.Root.getResources().getDisplayMetrics().density);
    }
}
