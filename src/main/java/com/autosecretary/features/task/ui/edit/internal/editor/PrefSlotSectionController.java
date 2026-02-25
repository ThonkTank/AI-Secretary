package com.autosecretary.features.task.ui.edit.internal.editor;

import android.app.TimePickerDialog;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.annotation.DimenRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.R;
import com.autosecretary.shared.Period;
import com.autosecretary.features.task.ui.edit.PrefSlotEditState;
import com.autosecretary.features.task.ui.edit.TaskEditPresenter;
import com.autosecretary.features.task.ui.edit.internal.PrefSlotUIBuilder;
import com.google.android.material.button.MaterialButton;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

public class PrefSlotSectionController {

    private static final int WEEK_DAY_COUNT = 7;
    private static final int DAY_PICKER_COLUMN_COUNT = 4;

    private final DialogFragment fragment;
    private final View rootView;
    private final TaskEditPresenter presenter;
    private final PrefSlotUIBuilder prefSlotUIBuilder;
    private final CheckBox toggleRepetition;
    private final EditText repsView;
    private final EditText perPeriodView;
    private final Spinner periodUnitView;

    private LinearLayout prefSlotContainer;

    public PrefSlotSectionController(
        DialogFragment fragment,
        View rootView,
        TaskEditPresenter presenter,
        CheckBox toggleRepetition,
        EditText repsView,
        EditText perPeriodView,
        Spinner periodUnitView
    ) {
        this.fragment = fragment;
        this.rootView = rootView;
        this.presenter = presenter;
        this.prefSlotUIBuilder = new PrefSlotUIBuilder(fragment.requireContext());
        this.toggleRepetition = toggleRepetition;
        this.repsView = repsView;
        this.perPeriodView = perPeriodView;
        this.periodUnitView = periodUnitView;
    }

    public void rebuildPrefSlotUI() {
        prefSlotContainer = rootView.findViewById(R.id.PrefSlotContainer);
        int repsPerDay = presenter.computeCurrentRepsPerDay(
            toggleRepetition.isChecked(),
            repsView.getText().toString(),
            perPeriodView.getText().toString(),
            (Period) periodUnitView.getSelectedItem()
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
            toggleRepetition.isChecked(),
            repsView.getText().toString(),
            perPeriodView.getText().toString(),
            (Period) periodUnitView.getSelectedItem()
        );
        if (changed) {
            rebuildPrefSlotUI();
        }
    }

    private void showDayPicker(PrefSlotEditState prefSlot, Set<DayOfWeek> takenByOthers) {
        DayOfWeek[] weekDays = {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        };
        String[] labels = fragment.getResources().getStringArray(R.array.task_edit_weekday_short_labels);

        if (labels.length != WEEK_DAY_COUNT) {
            throw new IllegalStateException("Expected exactly 7 localized weekday labels.");
        }

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

        boolean[] selected = new boolean[WEEK_DAY_COUNT];

        for (int i = 0; i < WEEK_DAY_COUNT; i++) {
            DayOfWeek day = weekDays[i];
            boolean isSelected = prefSlot.days != null && prefSlot.days.contains(day);
            boolean isTaken = takenByOthers.contains(day);

            selected[i] = isSelected;

            ContextThemeWrapper themedContext =
                new ContextThemeWrapper(fragment.requireContext(), R.style.Widget_AutoSecretary_TaskEdit_DayPickerButton);
            MaterialButton btn = new MaterialButton(themedContext, null, 0);
            btn.setText(labels[i]);
            btn.setMaxLines(2);
            btn.setMinWidth(0);
            btn.setMinimumWidth(0);
            btn.setCheckable(true);
            btn.setChecked(isSelected);
            btn.setInsetTop(0);
            btn.setInsetBottom(0);
            int dayButtonHorizontalPadding = dimenPx(R.dimen.task_editor_day_button_horizontal_padding);
            btn.setPadding(dayButtonHorizontalPadding, 0, dayButtonHorizontalPadding, 0);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(i / DAY_PICKER_COLUMN_COUNT, 1f),
                GridLayout.spec(i % DAY_PICKER_COLUMN_COUNT, 1f)
            );
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.setGravity(Gravity.FILL_HORIZONTAL);
            int dayButtonHorizontalMargin = dimenPx(R.dimen.task_editor_day_button_horizontal_margin);
            int dayButtonVerticalMargin = dimenPx(R.dimen.task_editor_day_button_vertical_margin);
            params.setMargins(
                dayButtonHorizontalMargin,
                dayButtonVerticalMargin,
                dayButtonHorizontalMargin,
                dayButtonVerticalMargin
            );
            btn.setLayoutParams(params);

            if (isTaken) {
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

        new AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.task_edit_day_picker_title)
            .setView(layout)
            .setPositiveButton(R.string.task_edit_day_picker_positive, (d, w) -> {
                EnumSet<DayOfWeek> newDays = EnumSet.noneOf(DayOfWeek.class);
                for (int i = 0; i < WEEK_DAY_COUNT; i++) {
                    if (selected[i]) {
                        newDays.add(weekDays[i]);
                    }
                }
                prefSlot.days = newDays;
                rebuildPrefSlotUI();
            })
            .setNegativeButton(R.string.task_edit_day_picker_negative, null)
            .show();
    }

    private void showTimePicker(PrefSlotEditState prefSlot) {
        int hour = prefSlot.start != null ? prefSlot.start.getHour() : 6;
        int minute = prefSlot.start != null ? prefSlot.start.getMinute() : 0;

        new TimePickerDialog(fragment.requireContext(), (picker, h, m) -> {
            prefSlot.start = LocalTime.of(h, m);
            rebuildPrefSlotUI();
        }, hour, minute, true).show();
    }

    private int dimenPx(@DimenRes int dimenResId) {
        Context context = fragment.requireContext();
        return context.getResources().getDimensionPixelSize(dimenResId);
    }
}
