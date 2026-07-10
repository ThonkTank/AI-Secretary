package com.autosecretary.features.task.ui.edit.internal.editor;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.Resources;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;

import java.time.LocalTime;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.R;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.ui.SpinnerHelper;
import com.autosecretary.features.task.ui.edit.state.PrefSlotEditState;
import com.autosecretary.features.task.ui.edit.TaskEditFormController;
import com.google.android.material.button.MaterialButton;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Orchestrates the preferred-slot section of the task-edit form.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Reads the current repetition-field values and asks the formController to compute
 *       {@code repsPerDay}, which determines how many slot groups are rendered.
 *   <li>Delegates actual UI construction to {@link PrefSlotUIBuilder}.
 *   <li>Shows day-picker ({@link android.app.AlertDialog}) and time-picker
 *       ({@link android.app.TimePickerDialog}) dialogs when the user taps a slot row.
 *   <li>Rebuilds the slot UI whenever repetition fields change via
 *       {@link #onRepetitionChanged()}.
 * </ul>
 *
 * <p>See {@code CLAUDE.md §Glossary} for definitions of PrefSlot and Repetition.
 */
public class PrefSlotSectionController {

    private static final int WEEK_DAY_COUNT = 7;
    private static final int DAY_PICKER_COLUMN_COUNT = 4;

    private final DialogFragment fragment;
    private final LinearLayout prefSlotContainer;
    private final TaskEditFormController formController;
    private final PrefSlotUIBuilder prefSlotUIBuilder;
    private final TaskEditSectionBinder.RepetitionViews repetitionViews;

    public PrefSlotSectionController(
        DialogFragment fragment,
        View rootView,
        TaskEditFormController formController,
        TaskEditSectionBinder.RepetitionViews repetitionViews
    ) {
        this.fragment = fragment;
        this.prefSlotContainer = rootView.findViewById(R.id.PrefSlotContainer);
        this.formController = formController;
        this.prefSlotUIBuilder = new PrefSlotUIBuilder(fragment.requireContext());
        this.repetitionViews = repetitionViews;
    }

    public void rebuildPrefSlotUI() {
        int repsPerDay = formController.computeCurrentRepsPerDay(
            repetitionViews.toggleRepetition.isChecked(),
            repetitionViews.repsView.getText().toString(),
            repetitionViews.perPeriodView.getText().toString(),
            SpinnerHelper.enumAtPosition(repetitionViews.periodUnitView, Period.values())
        );

        prefSlotUIBuilder.rebuild(prefSlotContainer, formController.getEditablePrefSlots(), repsPerDay,
            new PrefSlotUIBuilder.Listener() {
                @Override
                public void onDaysClicked(PrefSlotEditState prefSlot, Set<DayOfWeek> takenByOthers) {
                    showDayPicker(prefSlot, takenByOthers);
                }

                @Override
                public void onTimeClicked(PrefSlotEditState prefSlot) {
                    showTimePicker(prefSlot);
                }

                @Override
                public void onDeleteClicked(PrefSlotEditState prefSlot) {
                    formController.getEditablePrefSlots().remove(prefSlot);
                    rebuildPrefSlotUI();
                }
            });

        // "Add pref slot" button — manually adds a new row independent of repsPerDay.
        Context ctx = fragment.requireContext();
        MaterialButton addButton = new MaterialButton(ctx, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        addButton.setText(R.string.task_editor_add_pref_slot);
        addButton.setAllCaps(false);
        Resources res = ctx.getResources();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = res.getDimensionPixelSize(R.dimen.spacing_sm);
        addButton.setLayoutParams(params);
        addButton.setOnClickListener(v -> {
            PrefSlotEditState newSlot = new PrefSlotEditState();
            newSlot.start = LocalTime.of(6, 0);
            formController.getEditablePrefSlots().add(newSlot);
            rebuildPrefSlotUI();
        });
        prefSlotContainer.addView(addButton);
    }

    public void onRepetitionChanged() {
        if (formController.onRepetitionChanged(
            repetitionViews.toggleRepetition.isChecked(),
            repetitionViews.repsView.getText().toString(),
            repetitionViews.perPeriodView.getText().toString(),
            SpinnerHelper.enumAtPosition(repetitionViews.periodUnitView, Period.values())
        )) {
            rebuildPrefSlotUI();
        }
    }

    private void showDayPicker(PrefSlotEditState prefSlot, Set<DayOfWeek> takenByOthers) {
        DayOfWeek[] weekDays = DayOfWeek.values();
        String[] labels = fragment.getResources().getStringArray(R.array.task_edit_weekday_short_labels);
        if (labels.length != WEEK_DAY_COUNT) {
            throw new IllegalStateException("Expected exactly 7 localized weekday labels.");
        }
        // selected[] is a single-element mutable array so the AlertDialog positive-button lambda
        // can capture and read updated values — Java lambdas require effectively-final captures,
        // but array contents are mutable even when the reference is effectively final.
        boolean[] selected = new boolean[WEEK_DAY_COUNT];
        GridLayout layout = buildDayPickerLayout(weekDays, labels, prefSlot, takenByOthers, selected);

        new AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.task_edit_day_picker_title)
            .setView(layout)
            .setPositiveButton(R.string.action_save, (d, w) -> {
                EnumSet<DayOfWeek> newDays = EnumSet.noneOf(DayOfWeek.class);
                for (int i = 0; i < WEEK_DAY_COUNT; i++) {
                    if (selected[i]) newDays.add(weekDays[i]);
                }
                prefSlot.days = newDays;
                rebuildPrefSlotUI();
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private GridLayout buildDayPickerLayout(DayOfWeek[] weekDays, String[] labels,
                                            PrefSlotEditState prefSlot, Set<DayOfWeek> takenByOthers,
                                            boolean[] selected) {
        Context ctx = fragment.requireContext();
        Resources res = ctx.getResources();
        GridLayout layout = new GridLayout(ctx);
        layout.setColumnCount(DAY_PICKER_COLUMN_COUNT);
        int paddingH = res.getDimensionPixelSize(R.dimen.spacing_sm);
        int paddingV = res.getDimensionPixelSize(R.dimen.task_editor_day_picker_vertical_padding);
        layout.setPadding(paddingH, paddingV, paddingH, paddingV);
        layout.setUseDefaultMargins(false);
        layout.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

        for (int i = 0; i < WEEK_DAY_COUNT; i++) {
            DayOfWeek day = weekDays[i];
            boolean isSelected = prefSlot.days != null && prefSlot.days.contains(day);
            selected[i] = isSelected;

            MaterialButton btn = createDayButton(ctx, res, labels[i], isSelected, i);
            if (takenByOthers.contains(day)) {
                btn.setEnabled(false);
            } else {
                final int index = i;
                btn.setOnClickListener(v -> {
                    selected[index] = !selected[index];
                    btn.setChecked(selected[index]);
                });
            }
            layout.addView(btn);
        }
        return layout;
    }

    private MaterialButton createDayButton(Context ctx, Resources res, String label,
                                           boolean isSelected, int gridIndex) {
        // MaterialButton reads its style from the context theme at construction time.
        // Wrapping the context here is required — passing the style as a constructor argument
        // alone has no effect for programmatically-created MaterialButton instances.
        ContextThemeWrapper themedContext =
            new ContextThemeWrapper(ctx, R.style.Widget_AutoSecretary_TaskEdit_DayPickerButton);
        MaterialButton btn = new MaterialButton(themedContext, null, 0);
        btn.setText(label);
        btn.setMaxLines(2);
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setCheckable(true);
        btn.setChecked(isSelected);
        btn.setInsetTop(0);
        btn.setInsetBottom(0);
        int horizontalPadding = res.getDimensionPixelSize(R.dimen.spacing_xs);
        btn.setPadding(horizontalPadding, 0, horizontalPadding, 0);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
            GridLayout.spec(gridIndex / DAY_PICKER_COLUMN_COUNT, 1f),
            GridLayout.spec(gridIndex % DAY_PICKER_COLUMN_COUNT, 1f)
        );
        params.width = 0;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.setGravity(Gravity.FILL_HORIZONTAL);
        int horizontalMargin = res.getDimensionPixelSize(R.dimen.task_editor_day_button_horizontal_margin);
        int verticalMargin = res.getDimensionPixelSize(R.dimen.task_editor_day_button_vertical_margin);
        params.setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin);
        btn.setLayoutParams(params);

        return btn;
    }

    private void showTimePicker(PrefSlotEditState prefSlot) {
        // Default to 06:00 when no preferred time has been set yet — a reasonable
        // morning start that avoids midnight and is clearly a placeholder.
        LocalTime start = prefSlot.start != null ? prefSlot.start : LocalTime.of(6, 0);
        // true = 24-hour clock format (matches the HH:mm display in PrefSlotUIBuilder)
        new TimePickerDialog(fragment.requireContext(), (picker, h, m) -> {
            prefSlot.start = LocalTime.of(h, m);
            rebuildPrefSlotUI();
        }, start.getHour(), start.getMinute(), /* is24HourView= */ true).show();
    }
}
