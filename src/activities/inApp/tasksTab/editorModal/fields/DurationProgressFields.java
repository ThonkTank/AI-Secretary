package activities.inApp.tasksTab.editorModal.fields;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import static activities.generic.ViewHelper.*;

import com.autosecretary.R;

import java.util.function.BooleanSupplier;

import entities.TrackedItem;
import entities.TrackedItem.DurationUnit;

/**
 * Domain-Gruppe: Min/Max-Dauer mit Unit-Buttons, Completion-Toggle, Progress-Felder.
 */
public class DurationProgressFields implements FieldGroup {

    private final Context context;
    private final BooleanSupplier suppressCheck;

    // Duration
    private View minDurationRow;
    private View durationRow;
    private EditText minDurationField;
    private EditText durationField;
    private Button[] minDurationUnitButtons = new Button[2];
    private Button[] maxDurationUnitButtons = new Button[2];

    // Completion/Progress
    private View completionToggleRow;
    private CheckBox completionCheckbox;
    private View progressRow;
    private View progressPerRepRow;
    private Button progressPerRepToggle;
    private EditText progressCurrentField;
    private EditText progressTargetField;
    private EditText progressUnitField;

    private DurationUnit selectedMinUnit = DurationUnit.MINUTES;
    private DurationUnit selectedMaxUnit = DurationUnit.MINUTES;
    private boolean progressEnabled = false;
    private boolean progressPerRepEnabled = false;

    public DurationProgressFields(Context context, View root, BooleanSupplier suppressCheck,
                           Runnable onVisibilityTrigger) {
        this.context = context;
        this.suppressCheck = suppressCheck;
        bind(root, onVisibilityTrigger);
    }

    private void bind(View root, Runnable onVisibilityTrigger) {
        bindDurationFields(root);
        bindCompletionToggle(root, onVisibilityTrigger);
        bindProgressFields(root);
    }

    private void bindDurationFields(View root) {
        minDurationRow = root.findViewById(R.id.row_min_duration);
        minDurationField = root.findViewById(R.id.field_min_duration);
        durationRow = root.findViewById(R.id.row_duration);
        durationField = root.findViewById(R.id.field_duration);

        bindUnitButtons(root, minDurationUnitButtons,
            R.id.btn_min_unit_minutes, R.id.btn_min_unit_progress,
            idx -> { selectedMinUnit = DurationUnit.values()[idx]; });
        bindUnitButtons(root, maxDurationUnitButtons,
            R.id.btn_max_unit_minutes, R.id.btn_max_unit_progress,
            idx -> { selectedMaxUnit = DurationUnit.values()[idx]; });
    }

    private void bindUnitButtons(View root, Button[] buttons, int minutesId, int progressId,
                                 java.util.function.IntConsumer onSelect) {
        buttons[0] = root.findViewById(minutesId);
        buttons[1] = root.findViewById(progressId);
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            buttons[i].setOnClickListener(v -> {
                if (suppressCheck.getAsBoolean()) return;
                onSelect.accept(idx);
                updateButtonGroup(context, buttons, idx);
            });
        }
    }

    private void bindCompletionToggle(View root, Runnable onVisibilityTrigger) {
        completionToggleRow = root.findViewById(R.id.row_completion_toggle);
        completionCheckbox = root.findViewById(R.id.cb_completion);
        completionCheckbox.setOnCheckedChangeListener((btn, checked) -> {
            if (suppressCheck.getAsBoolean()) return;
            progressEnabled = checked;
            onVisibilityTrigger.run();
        });
    }

    private void bindProgressFields(View root) {
        progressRow = root.findViewById(R.id.row_progress);
        progressPerRepRow = root.findViewById(R.id.row_progress_per_rep);
        progressPerRepToggle = root.findViewById(R.id.btn_progress_per_rep);
        progressCurrentField = root.findViewById(R.id.field_progress_current);
        progressTargetField = root.findViewById(R.id.field_progress_target);
        progressUnitField = root.findViewById(R.id.field_progress_unit);

        progressPerRepToggle.setOnClickListener(v -> {
            if (suppressCheck.getAsBoolean()) return;
            progressPerRepEnabled = !progressPerRepEnabled;
            updateToggleButton(context, progressPerRepToggle, progressPerRepEnabled);
        });
    }

    // ========================================================================
    // FieldGroup
    // ========================================================================

    @Override
    public void populate(TrackedItem item) {
        if (item != null) {
            minDurationField.setText(item.minDurationValue > 0 ? String.valueOf(item.minDurationValue) : "");
            selectedMinUnit = item.minDurationUnit != null ? item.minDurationUnit : DurationUnit.MINUTES;
            durationField.setText(item.maxDurationValue > 0 ? String.valueOf(item.maxDurationValue) : "");
            selectedMaxUnit = item.maxDurationUnit != null ? item.maxDurationUnit : DurationUnit.MINUTES;

            progressEnabled = item.progressTarget > 0;
            progressCurrentField.setText(item.progressCurrent > 0 ? String.valueOf(item.progressCurrent) : "");
            progressTargetField.setText(progressEnabled ? String.valueOf(item.progressTarget) : "");
            progressUnitField.setText(item.progressUnit != null ? item.progressUnit : "");
            progressPerRepEnabled = item.progressPerRep;
        } else {
            minDurationField.setText("");
            selectedMinUnit = DurationUnit.MINUTES;
            durationField.setText("");
            selectedMaxUnit = DurationUnit.MINUTES;
            progressEnabled = false;
            progressCurrentField.setText("");
            progressTargetField.setText("");
            progressUnitField.setText("");
            progressPerRepEnabled = false;
        }
        updateButtonGroup(context, minDurationUnitButtons, selectedMinUnit.ordinal());
        updateButtonGroup(context, maxDurationUnitButtons, selectedMaxUnit.ordinal());
        completionCheckbox.setChecked(progressEnabled);
        updateToggleButton(context, progressPerRepToggle, progressPerRepEnabled);
    }

    @Override
    public void apply(TrackedItem.Builder builder) {
        String minVal = minDurationField.getText().toString().trim();
        if (!minVal.isEmpty()) {
            try { builder.minDuration(Integer.parseInt(minVal), selectedMinUnit); }
            catch (NumberFormatException ignored) {}
        }
        String maxVal = durationField.getText().toString().trim();
        if (!maxVal.isEmpty()) {
            try { builder.maxDuration(Integer.parseInt(maxVal), selectedMaxUnit); }
            catch (NumberFormatException ignored) {}
        }

        if (!progressEnabled) return;
        String target = progressTargetField.getText().toString().trim();
        if (!target.isEmpty()) {
            try { builder.progressTarget(Integer.parseInt(target)); }
            catch (NumberFormatException ignored) {}
        }
        String current = progressCurrentField.getText().toString().trim();
        if (!current.isEmpty()) {
            try { builder.progressCurrent(Integer.parseInt(current)); }
            catch (NumberFormatException ignored) {}
        }
        String unit = progressUnitField.getText().toString().trim();
        if (!unit.isEmpty()) builder.progressUnit(unit);
        builder.progressPerRep(progressPerRepEnabled);
    }

    @Override
    public void updateVisibility(VisibilityFlags flags) {
        minDurationRow.setVisibility(flags.showMinDuration() ? View.VISIBLE : View.GONE);
        durationRow.setVisibility(flags.showMaxDuration() ? View.VISIBLE : View.GONE);
        completionToggleRow.setVisibility(flags.showCompletionToggle() ? View.VISIBLE : View.GONE);
        progressRow.setVisibility(flags.showProgressFields() ? View.VISIBLE : View.GONE);
        progressPerRepRow.setVisibility(flags.showProgressFields() ? View.VISIBLE : View.GONE);
    }

    // ========================================================================
    // GETTER
    // ========================================================================

    public boolean isProgressEnabled() { return progressEnabled; }
}
