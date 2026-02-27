package com.autosecretary.features.task.ui.edit.internal.editor;

import android.app.TimePickerDialog;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;

import androidx.annotation.DimenRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.R;
import com.autosecretary.shared.Period;
import com.autosecretary.features.task.ui.edit.state.PrefSlotEditState;
import com.autosecretary.features.task.ui.edit.TaskEditPresenter;
import com.google.android.material.button.MaterialButton;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

public class PrefSlotSectionController {

    private static final int WEEK_DAY_COUNT = 7;
    private static final int DAY_PICKER_COLUMN_COUNT = 4;

    private final DialogFragment fragment;
    private final LinearLayout prefSlotContainer;
    private final TaskEditPresenter presenter;
    private final PrefSlotUIBuilder prefSlotUIBuilder;
    private final TaskEditSectionBinder.RepetitionViews repetitionViews;

    public PrefSlotSectionController(
        DialogFragment fragment,
        View rootView,
        TaskEditPresenter presenter,
        TaskEditSectionBinder.RepetitionViews repetitionViews
    ) {
        this.fragment = fragment;
        this.prefSlotContainer = rootView.findViewById(R.id.PrefSlotContainer);
        this.presenter = presenter;
        this.prefSlotUIBuilder = new PrefSlotUIBuilder(fragment.requireContext());
        this.repetitionViews = repetitionViews;
    }

    public void rebuildPrefSlotUI() {
        int repsPerDay = presenter.computeCurrentRepsPerDay(
            repetitionViews.toggleRepetition.isChecked(),
            repetitionViews.repsView.getText().toString(),
            repetitionViews.perPeriodView.getText().toString(),
            (Period) repetitionViews.periodUnitView.getSelectedItem()
        );

        prefSlotUIBuilder.rebuild(prefSlotContainer, presenter.getEditablePrefSlots(), repsPerDay,
            new PrefSlotUIBuilder.Listener() {
                @Override
                public void onDaysClicked(PrefSlotEditState prefSlot, Set<DayOfWeek> takenByOthers) {
                    showDayPicker(prefSlot, takenByOthers);
                }

                @Override
                public void onTimeClicked(PrefSlotEditState prefSlot) {
                    showTimePicker(prefSlot);
                }
            });
    }

    public void onRepetitionChanged() {
        boolean changed = presenter.onRepetitionChanged(
            repetitionViews.toggleRepetition.isChecked(),
            repetitionViews.repsView.getText().toString(),
            repetitionViews.perPeriodView.getText().toString(),
            (Period) repetitionViews.periodUnitView.getSelectedItem()
        );
        if (changed) {
            rebuildPrefSlotUI();
        }
    }

    private void showDayPicker(PrefSlotEditState prefSlot, Set<DayOfWeek> takenByOthers) {
        DayOfWeek[] weekDays = DayOfWeek.values();
        String[] labels = fragment.getResources().getStringArray(R.array.task_edit_weekday_short_labels);
        if (labels.length != WEEK_DAY_COUNT) {
            throw new IllegalStateException("Expected exactly 7 localized weekday labels.");
        }
        boolean[] selected = new boolean[WEEK_DAY_COUNT];
        GridLayout layout = buildDayPickerLayout(weekDays, labels, prefSlot, takenByOthers, selected);

        new AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.task_edit_day_picker_title)
            .setView(layout)
            .setPositiveButton(R.string.task_edit_day_picker_positive, (d, w) -> {
                EnumSet<DayOfWeek> newDays = EnumSet.noneOf(DayOfWeek.class);
                for (int i = 0; i < WEEK_DAY_COUNT; i++) {
                    if (selected[i]) newDays.add(weekDays[i]);
                }
                prefSlot.days = newDays;
                rebuildPrefSlotUI();
            })
            .setNegativeButton(R.string.task_edit_day_picker_negative, null)
            .show();
    }

    private GridLayout buildDayPickerLayout(DayOfWeek[] weekDays, String[] labels,
                                            PrefSlotEditState prefSlot, Set<DayOfWeek> takenByOthers,
                                            boolean[] selected) {
        GridLayout layout = new GridLayout(fragment.requireContext());
        layout.setColumnCount(DAY_PICKER_COLUMN_COUNT);
        layout.setPadding(
            dimenPx(R.dimen.task_editor_day_picker_horizontal_padding),
            dimenPx(R.dimen.task_editor_day_picker_vertical_padding),
            dimenPx(R.dimen.task_editor_day_picker_horizontal_padding),
            dimenPx(R.dimen.task_editor_day_picker_vertical_padding)
        );
        layout.setUseDefaultMargins(false);
        layout.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

        for (int i = 0; i < WEEK_DAY_COUNT; i++) {
            DayOfWeek day = weekDays[i];
            boolean isSelected = prefSlot.days != null && prefSlot.days.contains(day);
            selected[i] = isSelected;

            MaterialButton btn = createDayButton(labels[i], isSelected, i);
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

    private MaterialButton createDayButton(String label, boolean isSelected, int gridIndex) {
        ContextThemeWrapper themedContext =
            new ContextThemeWrapper(fragment.requireContext(), R.style.Widget_AISecretary_TaskEdit_DayPickerButton);
        MaterialButton btn = new MaterialButton(themedContext, null, 0);
        btn.setText(label);
        btn.setMaxLines(2);
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setCheckable(true);
        btn.setChecked(isSelected);
        btn.setInsetTop(0);
        btn.setInsetBottom(0);
        int horizontalPadding = dimenPx(R.dimen.task_editor_day_button_horizontal_padding);
        btn.setPadding(horizontalPadding, 0, horizontalPadding, 0);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
            GridLayout.spec(gridIndex / DAY_PICKER_COLUMN_COUNT, 1f),
            GridLayout.spec(gridIndex % DAY_PICKER_COLUMN_COUNT, 1f)
        );
        params.width = 0;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.setGravity(Gravity.FILL_HORIZONTAL);
        int horizontalMargin = dimenPx(R.dimen.task_editor_day_button_horizontal_margin);
        int verticalMargin = dimenPx(R.dimen.task_editor_day_button_vertical_margin);
        params.setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin);
        btn.setLayoutParams(params);

        return btn;
    }

    private void showTimePicker(PrefSlotEditState prefSlot) {
        LocalTime start = prefSlot.start != null ? prefSlot.start : LocalTime.of(6, 0);
        new TimePickerDialog(fragment.requireContext(), (picker, h, m) -> {
            prefSlot.start = LocalTime.of(h, m);
            rebuildPrefSlotUI();
        }, start.getHour(), start.getMinute(), true).show();
    }

    private int dimenPx(@DimenRes int dimenResId) {
        return fragment.requireContext().getResources().getDimensionPixelSize(dimenResId);
    }
}
