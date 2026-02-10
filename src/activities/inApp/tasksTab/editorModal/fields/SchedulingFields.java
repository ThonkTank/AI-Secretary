package activities.inApp.tasksTab.editorModal.fields;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;

import static activities.generic.ViewHelper.*;
import static activities.inApp.tasksTab.editorModal.fields.VisibilityFlags.*;

import com.autosecretary.R;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.function.BooleanSupplier;

import entities.TrackedItem;
import entities.TrackedItem.ItemType;
import entities.TrackedItem.RepetitionType;

/**
 * Domain-Gruppe: SchedulingType-Spinner, Fixed Appointment (Datum + Uhrzeit), Deadline.
 */
public class SchedulingFields implements FieldGroup {

    private static final String[] SCHED_OPTIONS_TASK = {"Termin", "Aufgabe", "Angewohnheit"};
    private static final String[] SCHED_OPTIONS_GOAL = {"Aufgabe", "Angewohnheit"};

    private final Context context;
    private final BooleanSupplier suppressCheck;
    private final Runnable onVisibilityTrigger;

    private Spinner schedulingTypeSpinner;
    private EditText deadlineField;
    private EditText fixedDateField;
    private EditText fixedTimeField;
    private View schedulingTypeRow;
    private View deadlineRow;
    private View fixedDateRow;
    private View fixedTimeRow;

    private int selectedSchedulingType = SCHED_AUFGABE;
    private ItemType lastType = null;

    public SchedulingFields(Context context, View root, BooleanSupplier suppressCheck,
                     Runnable onVisibilityTrigger) {
        this.context = context;
        this.suppressCheck = suppressCheck;
        this.onVisibilityTrigger = onVisibilityTrigger;
        bind(root);
    }

    private void bind(View root) {
        schedulingTypeRow = root.findViewById(R.id.row_scheduling_type);
        schedulingTypeSpinner = root.findViewById(R.id.spinner_scheduling_type);
        onSpinnerSelected(schedulingTypeSpinner, suppressCheck, pos -> {
            selectedSchedulingType = resolveSchedulingType(pos, lastType);
            onVisibilityTrigger.run();
        });

        deadlineRow = root.findViewById(R.id.row_deadline);
        deadlineField = root.findViewById(R.id.field_deadline);
        deadlineField.setOnClickListener(v -> {
            LocalDate init = LocalDate.now().plusDays(7);
            new DatePickerDialog(context, (dp, y, m, d) ->
                deadlineField.setText(LocalDate.of(y, m + 1, d).toString()),
                init.getYear(), init.getMonthValue() - 1, init.getDayOfMonth()).show();
        });

        fixedDateRow = root.findViewById(R.id.row_fixed_date);
        fixedDateField = root.findViewById(R.id.field_fixed_date);
        fixedDateField.setOnClickListener(v -> {
            LocalDate init = LocalDate.now().plusDays(1);
            new DatePickerDialog(context, (dp, y, m, d) ->
                fixedDateField.setText(LocalDate.of(y, m + 1, d).toString()),
                init.getYear(), init.getMonthValue() - 1, init.getDayOfMonth()).show();
        });

        fixedTimeRow = root.findViewById(R.id.row_fixed_time);
        fixedTimeField = root.findViewById(R.id.field_fixed_time);
        fixedTimeField.setOnClickListener(v ->
            new TimePickerDialog(context, (tp, h, m) ->
                fixedTimeField.setText(String.format("%02d:%02d", h, m)),
                12, 0, true).show());
    }

    // ========================================================================
    // FieldGroup
    // ========================================================================

    @Override
    public void populate(TrackedItem item) {
        if (item != null) {
            if (item.isFixedAppointment()) {
                selectedSchedulingType = SCHED_TERMIN;
            } else if (item.repetition != null && item.repetition.type != RepetitionType.NONE) {
                selectedSchedulingType = SCHED_ANGEWOHNHEIT;
            } else {
                selectedSchedulingType = SCHED_AUFGABE;
            }
            lastType = item.type;
            fixedDateField.setText(item.fixedDate != null ? item.fixedDate.toString() : "");
            fixedTimeField.setText(item.fixedTime != null ? item.fixedTime.toString() : "");
            deadlineField.setText(item.deadline != null ? item.deadline.toString() : "");
        } else {
            selectedSchedulingType = SCHED_AUFGABE;
            lastType = ItemType.TASK;
            fixedDateField.setText("");
            fixedTimeField.setText("");
            deadlineField.setText("");
        }
        updateSpinnerOptions(lastType);
        int spinnerPos = schedulingTypeToSpinnerPos(selectedSchedulingType, lastType);
        if (spinnerPos >= 0 && spinnerPos < schedulingTypeSpinner.getCount()) {
            schedulingTypeSpinner.setSelection(spinnerPos);
        }
    }

    @Override
    public void apply(TrackedItem.Builder builder) {
        if (selectedSchedulingType == SCHED_AUFGABE) {
            String dl = deadlineField.getText().toString().trim();
            if (!dl.isEmpty()) {
                try { builder.deadline(dl); }
                catch (DateTimeParseException ignored) {}
            }
        }
        if (selectedSchedulingType == SCHED_TERMIN) {
            String fd = fixedDateField.getText().toString().trim();
            String ft = fixedTimeField.getText().toString().trim();
            if (!fd.isEmpty() && !ft.isEmpty()) {
                try { builder.fixedAppointment(fd, ft); }
                catch (DateTimeParseException ignored) {}
            }
        }
    }

    @Override
    public void updateVisibility(VisibilityFlags flags) {
        schedulingTypeRow.setVisibility(flags.showSchedulingType() ? View.VISIBLE : View.GONE);
        deadlineRow.setVisibility(flags.showDeadline() ? View.VISIBLE : View.GONE);
        fixedDateRow.setVisibility(flags.showFixedFields() ? View.VISIBLE : View.GONE);
        fixedTimeRow.setVisibility(flags.showFixedFields() ? View.VISIBLE : View.GONE);

        if (flags.type() != lastType) {
            lastType = flags.type();
            updateSpinnerOptions(lastType);
            int spinnerPos = schedulingTypeToSpinnerPos(selectedSchedulingType, lastType);
            if (spinnerPos >= 0 && spinnerPos < schedulingTypeSpinner.getCount()) {
                schedulingTypeSpinner.setSelection(spinnerPos);
            }
        }
    }

    // ========================================================================
    // GETTER
    // ========================================================================

    public int getSchedulingType() { return selectedSchedulingType; }

    // ========================================================================
    // SPINNER-LOGIK
    // ========================================================================

    private void updateSpinnerOptions(ItemType selectedType) {
        String[] options = (selectedType == ItemType.TASK) ? SCHED_OPTIONS_TASK : SCHED_OPTIONS_GOAL;
        schedulingTypeSpinner.setAdapter(spinnerAdapter(context, options));
    }

    private int resolveSchedulingType(int spinnerPos, ItemType selectedType) {
        return (selectedType == ItemType.TASK) ? spinnerPos : spinnerPos + 1;
    }

    private int schedulingTypeToSpinnerPos(int schedType, ItemType selectedType) {
        return (selectedType == ItemType.TASK) ? schedType : schedType - 1;
    }
}
