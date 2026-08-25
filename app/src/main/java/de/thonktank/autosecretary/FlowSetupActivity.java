package de.thonktank.autosecretary;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.lifecycle.ViewModelProvider;

import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy;
import de.thonktank.autosecretary.domain.model.StepFlowSetup;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.TaskCatalog;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.presentation.legacy.LegacyStateFlowBinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Functional setup view; its ViewModel owns loading, drafts and persistence. */
public final class FlowSetupActivity extends ComponentActivity {
    private final List<StepRow> stepRows = new ArrayList<>();
    private final List<FlowSetupDraft.Lease> leaseDrafts = new ArrayList<>();
    private final List<CapacityResource> resources = new ArrayList<>();
    private UiStyle style;
    private DayPalette palette;
    private Spinner taskSpinner;
    private LinearLayout resourceList;
    private LinearLayout stepList;
    private LinearLayout leaseList;
    private TextView save;
    private TextView addLease;
    private TaskCatalog catalog;
    private StepFlowSetup setup;
    private FlowSetupDraft draft = FlowSetupDraft.empty();
    private FlowSetupViewModel viewModel;
    private boolean selectingTask;
    private int currentTaskIndex = -1;
    private long handledFeedbackId;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppContainer container = AutoSecretaryApplication.from(this).container();
        style = new UiStyle(this);
        palette = DayPalette.at(container.clock.time(), DayPalette.Mode.AUTO);
        viewModel = new ViewModelProvider(this,
                new FlowSetupViewModel.Factory(container)).get(FlowSetupViewModel.class);
        build();
        LegacyStateFlowBinder.observe(this, viewModel.state(), this::bindState);
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(style.dp(20), style.dp(24), style.dp(20), style.dp(36));
        page.setBackgroundColor(palette.background);
        TextView back = button(getString(R.string.flow_back), false);
        back.setOnClickListener(view -> finish());
        page.addView(back, params(-2, style.dp(48), 0));
        page.addView(style.serif(getString(R.string.flow_setup_title), 30, palette.ink,
                false, 300), params(-1, -2, 12));
        page.addView(banner(), params(-1, -2, 12));
        page.addView(style.sans(getString(R.string.flow_setup_description), 16,
                palette.ink2, false), params(-1, -2, 12));

        page.addView(section(R.string.flow_setup_task), params(-1, -2, 18));
        taskSpinner = new Spinner(this);
        taskSpinner.setMinimumHeight(style.dp(52));
        page.addView(taskSpinner, params(-1, style.dp(52), 6));

        page.addView(section(R.string.flow_setup_resources), params(-1, -2, 22));
        page.addView(style.sans(getString(R.string.flow_setup_resources_hint), 15,
                palette.hint, false));
        resourceList = vertical();
        page.addView(resourceList, params(-1, -2, 8));
        TextView addResource = button(getString(R.string.flow_resource_add), false);
        addResource.setOnClickListener(view -> editResource(null));
        page.addView(addResource, params(-2, style.dp(48), 8));

        page.addView(section(R.string.flow_setup_steps), params(-1, -2, 24));
        page.addView(style.sans(getString(R.string.flow_setup_steps_hint), 15,
                palette.hint, false));
        stepList = vertical();
        page.addView(stepList, params(-1, -2, 8));

        page.addView(section(R.string.flow_setup_capacity_rules), params(-1, -2, 24));
        page.addView(style.sans(getString(R.string.flow_setup_capacity_hint), 15,
                palette.hint, false));
        leaseList = vertical();
        page.addView(leaseList, params(-1, -2, 8));
        addLease = button(getString(R.string.flow_capacity_add), false);
        addLease.setOnClickListener(view -> editLease(null));
        page.addView(addLease, params(-2, style.dp(48), 8));

        page.addView(style.sans(getString(R.string.flow_setup_snapshot_note), 14,
                palette.hint, false), params(-1, -2, 22));
        save = button(getString(R.string.flow_setup_save), true);
        save.setOnClickListener(view -> save());
        page.addView(save, params(-1, style.dp(54), 14));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
    }

    private TextView banner() {
        TextView view = style.sans(getString(R.string.flow_functional_banner), 14,
                palette.ink2, true);
        view.setPadding(style.dp(14), style.dp(10), style.dp(14), style.dp(10));
        view.setBackground(style.pill(palette.leaf3, 12));
        return view;
    }

    private void bindState(FlowSetupScreenState state) {
        if (state == null) return;
        boolean catalogChanged = catalog != state.catalog;
        catalog = state.catalog;
        List<String> titles = new ArrayList<>();
        for (TaskCatalog.Item item : state.catalog.items) titles.add(item.task.title);
        if (titles.isEmpty()) titles.add(getString(R.string.flow_setup_no_tasks));
        if (catalogChanged) {
            selectingTask = true;
            taskSpinner.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item, titles));
            taskSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view,
                                                     int position, long id) {
                    if (!selectingTask && position != currentTaskIndex
                            && position < catalog.items.size()) {
                        captureDraft();
                        viewModel.dispatch(FlowSetupAction.selectTask(position));
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> parent) { }
            });
            selectingTask = false;
        }
        taskSpinner.setEnabled(!state.loading && !state.saving && !catalog.items.isEmpty());
        currentTaskIndex = state.selectedTaskIndex;
        if (currentTaskIndex >= 0) {
            selectingTask = true;
            taskSpinner.setSelection(currentTaskIndex, false);
            selectingTask = false;
        }
        bindSetup(state.setup, state.draft);
        save.setEnabled(!state.saving && state.setup != null && !state.setup.steps.isEmpty());
        save.setAlpha(save.isEnabled() ? 1f : .5f);
        if (state.feedback != null && state.feedback.id != handledFeedbackId) {
            handledFeedbackId = state.feedback.id;
            Toast.makeText(this, state.feedback.message, Toast.LENGTH_LONG).show();
            viewModel.dispatch(FlowSetupAction.acknowledgeFeedback(state.feedback.id));
            if (state.feedback.saved) finish();
        }
    }

    private void bindSetup(StepFlowSetup value, FlowSetupDraft valueDraft) {
        setup = value;
        draft = valueDraft;
        stepRows.clear();
        leaseDrafts.clear();
        resources.clear();
        if (value != null) {
            resources.addAll(value.resources);
            leaseDrafts.addAll(valueDraft.leases);
        }
        renderResources();
        renderSteps();
        renderLeases();
    }

    private void renderResources() {
        resourceList.removeAllViews();
        if (resources.isEmpty()) resourceList.addView(note(R.string.flow_resources_empty));
        for (CapacityResource resource : resources) {
            LinearLayout row = card();
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout words = vertical();
            words.addView(style.sans(resource.name, 17, palette.ink2, true));
            words.addView(style.sans(getResources().getQuantityString(
                    R.plurals.flow_resource_capacity_value, resource.capacity,
                    resource.capacity), 14, palette.hint, false));
            row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
            TextView edit = button(getString(R.string.flow_edit), false);
            edit.setOnClickListener(view -> editResource(resource));
            row.addView(edit, new LinearLayout.LayoutParams(-2, style.dp(48)));
            resourceList.addView(row, params(-1, -2, 7));
        }
        addLease.setEnabled(!resources.isEmpty() && setup != null && setup.steps.size() > 1);
        addLease.setAlpha(addLease.isEnabled() ? 1f : .5f);
    }

    private void renderSteps() {
        stepList.removeAllViews();
        stepRows.clear();
        if (setup == null || setup.steps.isEmpty()) {
            stepList.addView(note(R.string.flow_steps_empty));
            return;
        }
        Map<String, StepTransition> transitionBySource = new HashMap<>();
        for (StepTransition transition : draft.transitions)
            transitionBySource.put(transition.sourceStepId, transition);
        for (TaskStepTemplate step : setup.steps) {
            StepRow row = new StepRow(step, transitionBySource.get(step.id));
            stepRows.add(row);
            stepList.addView(row.view, params(-1, -2, 9));
        }
        updateRoleLabels();
    }

    private void renderLeases() {
        leaseList.removeAllViews();
        if (leaseDrafts.isEmpty()) leaseList.addView(note(R.string.flow_capacity_empty));
        for (FlowSetupDraft.Lease lease : new ArrayList<>(leaseDrafts)) {
            LinearLayout row = card();
            row.setOrientation(LinearLayout.VERTICAL);
            row.addView(style.sans(leaseSummary(lease), 16, palette.ink2, false));
            EditorFlowLayout actions = new EditorFlowLayout(this);
            TextView edit = button(getString(R.string.flow_edit), false);
            edit.setOnClickListener(view -> editLease(lease));
            actions.addView(edit);
            TextView remove = button(getString(R.string.flow_remove), false);
            remove.setOnClickListener(view -> {
                leaseDrafts.remove(lease);
                renderLeases();
                captureDraft();
            });
            actions.addView(remove);
            row.addView(actions, params(-1, -2, 8));
            leaseList.addView(row, params(-1, -2, 7));
        }
    }

    private void editResource(CapacityResource existing) {
        LinearLayout form = dialogForm();
        EditText name = input(existing == null ? "" : existing.name,
                InputType.TYPE_CLASS_TEXT);
        form.addView(labeled(R.string.flow_resource_name, name));
        EditText capacity = input(existing == null ? "1" : String.valueOf(existing.capacity),
                InputType.TYPE_CLASS_NUMBER);
        form.addView(labeled(R.string.flow_resource_capacity_label, capacity));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.flow_resource_add
                        : R.string.flow_resource_edit)
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.flow_save, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    int parsed;
                    try { parsed = Integer.parseInt(capacity.getText().toString().trim()); }
                    catch (RuntimeException invalid) { parsed = 0; }
                    if (name.getText().toString().trim().isEmpty() || parsed < 1) {
                        capacity.setError(getString(R.string.flow_resource_invalid));
                        return;
                    }
                    dialog.dismiss();
                    saveResource(existing, name.getText().toString(), parsed);
                }));
        dialog.show();
    }

    private void saveResource(CapacityResource existing, String name, int capacity) {
        captureDraft();
        viewModel.dispatch(FlowSetupAction.saveResource(
                existing == null ? null : existing.id, name, capacity));
    }

    private void editLease(FlowSetupDraft.Lease existing) {
        if (resources.isEmpty() || setup == null || setup.steps.size() < 2) return;
        FlowSetupDraft.Lease initial = existing == null ? new FlowSetupDraft.Lease(
                UUID.randomUUID().toString(),
                resources.get(0).id, setup.steps.get(0).id,
                setup.steps.get(setup.steps.size() - 1).id, 1) : existing;
        LinearLayout form = dialogForm();
        Spinner resource = spinner(resourceNames(), indexOfResource(initial.resourceId));
        List<String> acquireNames = new ArrayList<>();
        if (existing == null) acquireNames.add(getString(R.string.flow_lease_all_starts));
        acquireNames.addAll(stepNames());
        Spinner acquire = spinner(acquireNames, existing == null ? 0
                : indexOfStep(initial.acquireStepId));
        Spinner release = spinner(stepNames(), indexOfStep(initial.releaseStepId));
        EditText units = input(String.valueOf(initial.units), InputType.TYPE_CLASS_NUMBER);
        form.addView(labeled(R.string.flow_lease_resource, resource));
        form.addView(labeled(R.string.flow_lease_units, units));
        form.addView(labeled(R.string.flow_lease_acquire, acquire));
        form.addView(labeled(R.string.flow_lease_release, release));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.flow_capacity_add
                        : R.string.flow_capacity_edit)
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.flow_save, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    int count;
                    try { count = Integer.parseInt(units.getText().toString().trim()); }
                    catch (RuntimeException invalid) { count = 0; }
                    if (count < 1) {
                        units.setError(getString(R.string.flow_lease_invalid));
                        return;
                    }
                    String resourceId = resources.get(resource.getSelectedItemPosition()).id;
                    String releaseId = setup.steps.get(release.getSelectedItemPosition()).id;
                    if (existing == null && acquire.getSelectedItemPosition() == 0) {
                        List<TaskStepTemplate> starts = selectedStartSteps();
                        if (starts.isEmpty()) {
                            units.setError(getString(R.string.flow_lease_no_starts));
                            return;
                        }
                        for (TaskStepTemplate start : starts)
                            putLease(new FlowSetupDraft.Lease(UUID.randomUUID().toString(), resourceId,
                                    start.id, releaseId, count));
                    } else {
                        int acquireIndex = acquire.getSelectedItemPosition()
                                - (existing == null ? 1 : 0);
                        FlowSetupDraft.Lease changed = new FlowSetupDraft.Lease(initial.id, resourceId,
                                setup.steps.get(acquireIndex).id, releaseId, count);
                        if (existing == null) putLease(changed);
                        else leaseDrafts.set(leaseDrafts.indexOf(existing), changed);
                    }
                    dialog.dismiss();
                    renderLeases();
                    captureDraft();
                }));
        dialog.show();
    }

    private List<TaskStepTemplate> selectedStartSteps() {
        Set<String> targets = new HashSet<>();
        for (StepRow row : stepRows) if (row.successorId() != null)
            targets.add(row.successorId());
        List<TaskStepTemplate> result = new ArrayList<>();
        for (TaskStepTemplate step : setup.steps)
            if (!targets.contains(step.id)) result.add(step);
        return result;
    }

    private void putLease(FlowSetupDraft.Lease changed) {
        leaseDrafts.removeIf(value -> value.resourceId.equals(changed.resourceId)
                && value.acquireStepId.equals(changed.acquireStepId)
                && value.releaseStepId.equals(changed.releaseStepId));
        leaseDrafts.add(changed);
    }

    private void save() {
        if (setup == null) return;
        try {
            FlowSetupDraft currentDraft = currentDraft();
            viewModel.dispatch(FlowSetupAction.updateDraft(currentDraft));
            viewModel.dispatch(FlowSetupAction.save());
        } catch (RuntimeException error) {
            showError(error);
        }
    }

    private FlowSetupDraft currentDraft() {
        List<StepTransition> transitions = new ArrayList<>();
        for (StepRow row : stepRows) {
            StepTransition transition = row.transition();
            if (transition != null) transitions.add(transition);
        }
        return new FlowSetupDraft(transitions, leaseDrafts);
    }

    private void captureDraft() {
        if (setup == null || viewModel == null) return;
        try {
            viewModel.dispatch(FlowSetupAction.updateDraft(currentDraft()));
        } catch (RuntimeException ignored) {
            // Half-entered duration text remains in the widget; Save will surface validation.
        }
    }

    @Override protected void onPause() {
        captureDraft();
        super.onPause();
    }

    private void updateRoleLabels() {
        Set<String> targets = new HashSet<>();
        for (StepRow row : stepRows) if (row.successorId() != null)
            targets.add(row.successorId());
        for (StepRow row : stepRows) row.role.setText(targets.contains(row.step.id)
                ? R.string.flow_role_follow_up : R.string.flow_role_scheduled);
    }

    private String leaseSummary(FlowSetupDraft.Lease lease) {
        return getString(R.string.flow_lease_summary, lease.units,
                resourceName(lease.resourceId), stepName(lease.acquireStepId),
                stepName(lease.releaseStepId));
    }

    private String resourceName(String id) {
        for (CapacityResource value : resources) if (value.id.equals(id)) return value.name;
        return getString(R.string.flow_missing_value);
    }

    private String stepName(String id) {
        if (setup != null) for (TaskStepTemplate value : setup.steps)
            if (value.id.equals(id)) return value.text;
        return getString(R.string.flow_missing_value);
    }

    private List<String> resourceNames() {
        List<String> result = new ArrayList<>();
        for (CapacityResource value : resources) result.add(value.name);
        return result;
    }

    private List<String> stepNames() {
        List<String> result = new ArrayList<>();
        for (TaskStepTemplate value : setup.steps) result.add(value.text);
        return result;
    }

    private int indexOfResource(String id) {
        for (int index = 0; index < resources.size(); index++)
            if (resources.get(index).id.equals(id)) return index;
        return 0;
    }

    private int indexOfStep(String id) {
        for (int index = 0; index < setup.steps.size(); index++)
            if (setup.steps.get(index).id.equals(id)) return index;
        return 0;
    }

    private LinearLayout card() {
        LinearLayout card = vertical();
        card.setPadding(style.dp(16), style.dp(14), style.dp(16), style.dp(14));
        card.setBackground(style.leaf(palette.leaf2, style.edge(palette, 2), 12, 34, 12, 34));
        return card;
    }

    private LinearLayout vertical() {
        LinearLayout value = new LinearLayout(this);
        value.setOrientation(LinearLayout.VERTICAL);
        return value;
    }

    private TextView section(int text) {
        return style.serif(getString(text), 22, palette.ink, true, 300);
    }

    private TextView note(int text) {
        return style.sans(getString(text), 15, palette.hint, false);
    }

    private TextView button(String text, boolean primary) {
        TextView view = style.sans(text, 16, primary ? palette.accentText : palette.ink2, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(style.dp(16), 0, style.dp(16), 0);
        view.setMinHeight(style.dp(48));
        view.setBackground(style.pill(primary ? palette.accent : palette.leaf1, 24));
        AccessibilityRoles.button(view);
        return view;
    }

    private LinearLayout dialogForm() {
        LinearLayout result = vertical();
        result.setPadding(style.dp(22), 0, style.dp(22), 0);
        return result;
    }

    private LinearLayout labeled(int label, View control) {
        LinearLayout result = vertical();
        result.addView(style.sans(getString(label), 14, palette.ink2, true));
        result.addView(control, params(-1, -2, 3));
        return result;
    }

    private EditText input(String text, int type) {
        EditText value = new EditText(this);
        value.setText(text);
        value.setInputType(type);
        value.setSingleLine(true);
        return value;
    }

    private Spinner spinner(List<String> labels, int selected) {
        Spinner value = new Spinner(this);
        value.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        value.setSelection(Math.max(0, Math.min(selected, labels.size() - 1)));
        return value;
    }

    private LinearLayout.LayoutParams params(int width, int height, int top) {
        LinearLayout.LayoutParams result = new LinearLayout.LayoutParams(width, height);
        result.setMargins(0, style.dp(top), 0, 0);
        return result;
    }

    private void showError(RuntimeException error) {
        String message = error.getMessage();
        Toast.makeText(this, message == null || message.trim().isEmpty()
                ? getString(R.string.error_change_save) : message, Toast.LENGTH_LONG).show();
    }

    private final class StepRow {
        final TaskStepTemplate step;
        final LinearLayout view;
        final TextView role;
        final Spinner successor;
        final List<String> successorIds = new ArrayList<>();
        final Spinner delayMode;
        final EditText delayValue;
        final Spinner delayUnit;
        final LinearLayout delayControls;

        StepRow(TaskStepTemplate step, StepTransition transition) {
            this.step = step;
            view = card();
            view.addView(style.sans(step.text, 18, palette.ink, true));
            role = style.sans("", 14, palette.hint, false);
            view.addView(role, params(-1, -2, 2));
            List<String> successors = new ArrayList<>();
            successors.add(getString(R.string.flow_step_end));
            successorIds.add(null);
            for (TaskStepTemplate candidate : setup.steps)
                if (!candidate.id.equals(step.id)) {
                    successors.add(candidate.text);
                    successorIds.add(candidate.id);
                }
            successor = spinner(successors, transition == null ? 0
                    : successorIds.indexOf(transition.targetStepId));
            view.addView(labeled(R.string.flow_step_after, successor),
                    params(-1, -2, 8));

            delayControls = vertical();
            List<String> modes = java.util.Arrays.asList(
                    getString(R.string.flow_delay_immediate),
                    getString(R.string.flow_delay_fixed),
                    getString(R.string.flow_delay_remember));
            int mode = transition == null || transition.delay.mode == FlowDelayPolicy.Mode.FIXED
                    && transition.delay.defaultDelayMillis == 0L ? 0
                    : transition.delay.mode == FlowDelayPolicy.Mode.FIXED ? 1 : 2;
            delayMode = spinner(modes, mode);
            delayControls.addView(labeled(R.string.flow_step_wait, delayMode));
            long duration = transition == null ? 60L * 60L * 1_000L
                    : transition.delay.proposedDelayMillis();
            FlowDurationDialog.Unit unit = FlowDurationDialog.Unit.bestFor(duration);
            LinearLayout durationRow = new LinearLayout(FlowSetupActivity.this);
            durationRow.setGravity(Gravity.CENTER_VERTICAL);
            delayValue = input(String.valueOf(unit.value(duration)), InputType.TYPE_CLASS_NUMBER);
            durationRow.addView(delayValue, new LinearLayout.LayoutParams(0, -2, 1));
            delayUnit = spinner(java.util.Arrays.asList(getString(R.string.flow_unit_minutes),
                    getString(R.string.flow_unit_hours), getString(R.string.flow_unit_days)),
                    unit.ordinal());
            durationRow.addView(delayUnit, new LinearLayout.LayoutParams(-2, -2));
            delayControls.addView(durationRow, params(-1, -2, 3));
            view.addView(delayControls, params(-1, -2, 6));
            updateDelayVisibility();
            successor.setOnItemSelectedListener(new SimpleSelection() {
                @Override public void selected() {
                    delayControls.setVisibility(successor.getSelectedItemPosition() == 0
                            ? View.GONE : View.VISIBLE);
                    updateRoleLabels();
                }
            });
            delayMode.setOnItemSelectedListener(new SimpleSelection() {
                @Override public void selected() { updateDelayVisibility(); }
            });
        }

        String successorId() {
            int position = successor.getSelectedItemPosition();
            return position < 0 || position >= successorIds.size() ? null
                    : successorIds.get(position);
        }

        StepTransition transition() {
            String target = successorId();
            if (target == null) return null;
            int mode = delayMode.getSelectedItemPosition();
            FlowDelayPolicy delay;
            if (mode == 0) delay = FlowDelayPolicy.fixed(0L);
            else {
                Long duration = FlowDurationDialog.parse(delayValue.getText().toString(),
                        FlowDurationDialog.Unit.values()[delayUnit.getSelectedItemPosition()]);
                if (duration == null)
                    throw new IllegalArgumentException(getString(R.string.flow_delay_invalid));
                delay = mode == 1 ? FlowDelayPolicy.fixed(duration)
                        : FlowDelayPolicy.rememberLast(duration);
            }
            return new StepTransition(step.id, target, delay);
        }

        private void updateDelayVisibility() {
            boolean successorSelected = successor.getSelectedItemPosition() > 0;
            delayControls.setVisibility(successorSelected ? View.VISIBLE : View.GONE);
            boolean duration = delayMode.getSelectedItemPosition() != 0;
            delayValue.setVisibility(duration ? View.VISIBLE : View.GONE);
            delayUnit.setVisibility(duration ? View.VISIBLE : View.GONE);
        }
    }

    private abstract static class SimpleSelection implements AdapterView.OnItemSelectedListener {
        abstract void selected();
        @Override public void onItemSelected(AdapterView<?> parent, View view,
                                             int position, long id) { selected(); }
        @Override public void onNothingSelected(AdapterView<?> parent) { }
    }

}
