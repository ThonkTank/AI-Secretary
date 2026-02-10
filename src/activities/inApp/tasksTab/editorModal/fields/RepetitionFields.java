package activities.inApp.tasksTab.editorModal.fields;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import static activities.generic.ViewHelper.*;

import com.autosecretary.R;

import java.time.DayOfWeek;
import java.util.function.BooleanSupplier;

import entities.TrackedItem;
import entities.TrackedItem.RepetitionType;
import entities.TrackedItem.RepUnits;

/**
 * Domain-Gruppe: RepType/RepUnit-Buttons, Wiederholungswert, Wochentag-Spinner,
 * CompleteFirst-Toggle.
 */
public class RepetitionFields implements FieldGroup {

    private static final RepetitionType[] REP_TYPES =
        {RepetitionType.INTERVAL, RepetitionType.REPS_PER_TIME, RepetitionType.DAY_OF_TIME};
    private static final RepUnits[] REP_UNITS =
        {RepUnits.DAY, RepUnits.WEEK, RepUnits.MONTH};

    private final Context context;
    private final BooleanSupplier suppressCheck;

    private LinearLayout repetitionSection;
    private View weekdayRow;
    private View completeFirstRow;
    private Button completeFirstToggle;
    private Button[] repTypeButtons = new Button[3];
    private Button[] repUnitButtons = new Button[3];
    private EditText repValueField;
    private Spinner weekdaySpinner;

    private RepetitionType selectedRepType = RepetitionType.INTERVAL;
    private RepUnits selectedRepUnit = RepUnits.DAY;
    private boolean completeFirstEnabled = false;

    public RepetitionFields(Context context, View root, BooleanSupplier suppressCheck,
                     Runnable onVisibilityTrigger) {
        this.context = context;
        this.suppressCheck = suppressCheck;
        bind(root, onVisibilityTrigger);
    }

    private void bind(View root, Runnable onVisibilityTrigger) {
        repetitionSection = root.findViewById(R.id.section_repetition);
        weekdayRow = root.findViewById(R.id.row_weekday);

        repValueField = root.findViewById(R.id.field_rep_value);
        weekdaySpinner = root.findViewById(R.id.spinner_weekday);
        String[] days = {"Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"};
        weekdaySpinner.setAdapter(spinnerAdapter(context, days));

        repTypeButtons[0] = root.findViewById(R.id.btn_rep_interval);
        repTypeButtons[1] = root.findViewById(R.id.btn_rep_reps);
        repTypeButtons[2] = root.findViewById(R.id.btn_rep_day_of);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            repTypeButtons[i].setOnClickListener(v -> {
                if (suppressCheck.getAsBoolean()) return;
                selectedRepType = REP_TYPES[idx];
                updateButtonGroup(context, repTypeButtons, idx);
                onVisibilityTrigger.run();
            });
        }

        repUnitButtons[0] = root.findViewById(R.id.btn_unit_day);
        repUnitButtons[1] = root.findViewById(R.id.btn_unit_week);
        repUnitButtons[2] = root.findViewById(R.id.btn_unit_month);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            repUnitButtons[i].setOnClickListener(v -> {
                if (suppressCheck.getAsBoolean()) return;
                selectedRepUnit = REP_UNITS[idx];
                updateButtonGroup(context, repUnitButtons, idx);
                onVisibilityTrigger.run();
            });
        }

        completeFirstRow = root.findViewById(R.id.row_complete_first);
        completeFirstToggle = root.findViewById(R.id.btn_complete_first);
        completeFirstToggle.setOnClickListener(v -> {
            if (suppressCheck.getAsBoolean()) return;
            completeFirstEnabled = !completeFirstEnabled;
            updateToggleButton(context, completeFirstToggle, completeFirstEnabled);
        });
    }

    // ========================================================================
    // FieldGroup
    // ========================================================================

    @Override
    public void populate(TrackedItem item) {
        if (item != null && item.repetition != null && item.repetition.type != RepetitionType.NONE) {
            selectedRepType = item.repetition.type;
            selectedRepUnit = item.repetition.unit;
            repValueField.setText(String.valueOf(item.repetition.value));
            DayOfWeek wd = item.repetition.dayOfWeek != null ? item.repetition.dayOfWeek : DayOfWeek.MONDAY;
            weekdaySpinner.setSelection(wd.getValue() - 1);
            completeFirstEnabled = item.completeFirst != null && item.completeFirst;
        } else {
            selectedRepType = RepetitionType.INTERVAL;
            selectedRepUnit = RepUnits.DAY;
            repValueField.setText("");
            weekdaySpinner.setSelection(0);
            completeFirstEnabled = false;
        }

        int repIdx = switch (selectedRepType) {
            case INTERVAL -> 0;
            case REPS_PER_TIME -> 1;
            case DAY_OF_TIME -> 2;
            case NONE -> 0;
        };
        updateButtonGroup(context, repTypeButtons, repIdx);
        updateButtonGroup(context, repUnitButtons, selectedRepUnit.ordinal());
        updateToggleButton(context, completeFirstToggle, completeFirstEnabled);
    }

    @Override
    public void apply(TrackedItem.Builder builder) {
        // Guard: Wenn Repetition-Sektion unsichtbar → explizit keine Wiederholung
        if (repetitionSection.getVisibility() != View.VISIBLE) {
            builder.noRepetition();
            builder.completeFirst(false);
            return;
        }

        builder.completeFirst(completeFirstEnabled);

        String val = repValueField.getText().toString().trim();
        if (val.isEmpty()) return;
        try {
            int repValue = Integer.parseInt(val);
            if (selectedRepType == RepetitionType.DAY_OF_TIME && selectedRepUnit == RepUnits.WEEK) {
                DayOfWeek weekday = DayOfWeek.of(weekdaySpinner.getSelectedItemPosition() + 1);
                builder.repetition(selectedRepType, repValue, selectedRepUnit, weekday);
            } else {
                builder.repetition(selectedRepType, repValue, selectedRepUnit);
            }
        } catch (NumberFormatException ignored) {}
    }

    @Override
    public void updateVisibility(VisibilityFlags flags) {
        repetitionSection.setVisibility(flags.showRepetition() ? View.VISIBLE : View.GONE);
        weekdayRow.setVisibility(flags.showWeekday() ? View.VISIBLE : View.GONE);
        completeFirstRow.setVisibility(flags.showCompleteFirst() ? View.VISIBLE : View.GONE);
    }

    // ========================================================================
    // GETTER
    // ========================================================================

    public RepetitionType getRepType() { return selectedRepType; }
    public RepUnits getRepUnit() { return selectedRepUnit; }
    public String getRepValue() { return repValueField.getText().toString().trim(); }
    public boolean isCompleteFirstEnabled() { return completeFirstEnabled; }
}
