package de.thonktank.autosecretary;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.util.ArrayList;
import java.util.Arrays;

public final class TaskEditorDialog {
    public interface Listener {
        void onDraftChanged(EditorUiState draft);
        void onSave(EditorUiState draft);
        void onDismiss();
    }

    private final Context context;
    private final EditorUiState initial;
    private final Listener listener;
    private final TaskEditorValidator validator = new TaskEditorValidator();
    private final Typeface font;
    private EditText name;
    private Spinner slot;
    private Spinner recurrence;
    private EditText interval;
    private final CheckBox[] weekdays = new CheckBox[7];
    private EditText steps;
    private CheckBox ongoing;
    private EditText condition;
    private boolean binding = true;

    private TaskEditorDialog(Context context, EditorUiState initial, Listener listener) {
        this.context = context;
        this.initial = initial;
        this.listener = listener;
        this.font = context.getResources().getFont(R.font.alegreya_sans);
    }

    public static AlertDialog show(Context context, EditorUiState state, Listener listener) {
        return new TaskEditorDialog(context, state, listener).show();
    }

    private AlertDialog show() {
        ScrollView scroll = new ScrollView(context);
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(22);
        form.setPadding(padding, dp(8), padding, dp(8));
        scroll.addView(form, new ScrollView.LayoutParams(-1, -2));

        name = input(R.string.editor_name_hint);
        name.setText(initial.title);
        form.addView(name);
        slot = spinner(new String[]{string(R.string.slot_morning), string(R.string.slot_midday),
                string(R.string.slot_evening), string(R.string.slot_later)});
        slot.setSelection(initial.slot.ordinal());
        label(form, R.string.editor_slot_label);
        form.addView(slot);

        recurrence = spinner(new String[]{string(R.string.recurrence_once),
                string(R.string.recurrence_daily), string(R.string.recurrence_interval),
                string(R.string.recurrence_weekdays)});
        recurrence.setSelection(initial.recurrence.ordinal());
        label(form, R.string.editor_recurrence_label);
        form.addView(recurrence);
        interval = input(R.string.editor_interval_hint);
        interval.setInputType(InputType.TYPE_CLASS_NUMBER);
        interval.setText(String.valueOf(initial.intervalDays));
        form.addView(interval);

        label(form, R.string.editor_weekdays_label);
        LinearLayout days = new LinearLayout(context);
        int[] dayLabels = {R.string.weekday_monday, R.string.weekday_tuesday,
                R.string.weekday_wednesday, R.string.weekday_thursday,
                R.string.weekday_friday, R.string.weekday_saturday, R.string.weekday_sunday};
        for (int i = 0; i < weekdays.length; i++) {
            weekdays[i] = new CheckBox(context);
            weekdays[i].setText(dayLabels[i]);
            weekdays[i].setChecked((initial.weekdayMask & (1 << i)) != 0);
            days.addView(weekdays[i]);
        }
        form.addView(days);

        steps = input(R.string.editor_steps_hint);
        steps.setMinLines(3);
        steps.setGravity(Gravity.TOP);
        steps.setText(android.text.TextUtils.join("\n", initial.steps));
        form.addView(steps);
        ongoing = new CheckBox(context);
        ongoing.setText(R.string.editor_ongoing);
        ongoing.setChecked(initial.ongoing);
        form.addView(ongoing);
        condition = input(R.string.editor_condition_hint);
        condition.setText(initial.condition);
        form.addView(condition);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(initial.taskId == null ? R.string.editor_new_title : R.string.editor_edit_title)
                .setView(scroll)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        attachDraftListeners();
        binding = false;
        dialog.setOnShowListener(value -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> save(dialog)));
        dialog.setOnDismissListener(value -> listener.onDismiss());
        dialog.show();
        return dialog;
    }

    private void attachDraftListeners() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { emit(); }
            @Override public void afterTextChanged(Editable value) { }
        };
        name.addTextChangedListener(watcher);
        interval.addTextChangedListener(watcher);
        steps.addTextChangedListener(watcher);
        condition.addTextChangedListener(watcher);
        android.widget.AdapterView.OnItemSelectedListener selected = new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) { emit(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        };
        slot.setOnItemSelectedListener(selected);
        recurrence.setOnItemSelectedListener(selected);
        for (CheckBox weekday : weekdays) weekday.setOnCheckedChangeListener((button, checked) -> emit());
        ongoing.setOnCheckedChangeListener((button, checked) -> emit());
    }

    private void save(AlertDialog dialog) {
        EditorUiState draft = draft();
        TaskEditorValidator.Error error = validator.validate(draft);
        if (error == TaskEditorValidator.Error.TITLE) {
            name.setError(string(R.string.error_name));
            name.requestFocus();
            return;
        }
        if (error == TaskEditorValidator.Error.WEEKDAYS) {
            recurrence.requestFocus();
            name.setError(string(R.string.error_weekdays));
            return;
        }
        if (error == TaskEditorValidator.Error.CONDITION) {
            condition.setError(string(R.string.error_condition));
            condition.requestFocus();
            return;
        }
        listener.onSave(draft);
        dialog.dismiss();
    }

    private void emit() {
        if (!binding) listener.onDraftChanged(draft());
    }

    private EditorUiState draft() {
        int weekdayMask = 0;
        for (int i = 0; i < weekdays.length; i++) if (weekdays[i].isChecked()) weekdayMask |= 1 << i;
        int intervalDays = positiveInt(interval.getText().toString(), 2);
        return initial.withDraft(name.getText().toString(),
                TaskSlot.values()[slot.getSelectedItemPosition()],
                Recurrence.values()[recurrence.getSelectedItemPosition()], intervalDays,
                weekdayMask, new ArrayList<>(Arrays.asList(steps.getText().toString().split("\\n"))),
                ongoing.isChecked(), condition.getText().toString());
    }

    private EditText input(int hint) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setTypeface(font);
        input.setMinHeight(dp(48));
        return input;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(context);
        spinner.setMinimumHeight(dp(48));
        spinner.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item, values));
        return spinner;
    }

    private void label(LinearLayout form, int text) {
        android.widget.TextView label = new android.widget.TextView(context);
        label.setText(text);
        label.setTypeface(font, Typeface.BOLD);
        label.setPadding(0, dp(14), 0, dp(3));
        form.addView(label);
    }

    private String string(int id) { return context.getString(id); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private static int positiveInt(String value, int fallback) {
        try { int parsed = Integer.parseInt(value); return parsed > 0 ? parsed : fallback; }
        catch (NumberFormatException error) { return fallback; }
    }
}
