package de.thonktank.autosecretary.presentation;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.thonktank.autosecretary.EditorStepState;
import de.thonktank.autosecretary.EditorUiState;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.StepCadenceMode;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TimeOfDay;

/** Localized task-editor summaries shared by the summary and step-list views. */
public final class TaskEditorTextFormatter {
    private final UiTextProvider texts;
    private final StepTextFormatter stepFormatter;
    private final DateTimeFormatter date;

    public TaskEditorTextFormatter(UiTextProvider texts) {
        this.texts = texts;
        stepFormatter = new StepTextFormatter(texts);
        date = DateTimeFormatter.ofPattern(texts.text(R.string.editor_date_pattern),
                Locale.GERMANY);
    }

    public String summaryLine(EditorUiState state) {
        List<String> values = new ArrayList<>();
        String rhythm = rhythm(state);
        if (state.recurrence == Recurrence.WEEKDAYS && state.weekdayMask == 31)
            rhythm = texts.text(R.string.editor_weekdays_workweek);
        values.add(state.recurrence == Recurrence.ONCE ? rhythm
                : texts.text(R.string.editor_summary_rhythm_time, rhythm, time(state)));
        if (state.estimatedMinutes != null)
            values.add(texts.text(R.string.editor_summary_duration, state.estimatedMinutes));
        values.add(texts.text(R.string.editor_summary_steps, state.stepStates.size()));
        return join(values);
    }

    public String rhythm(EditorUiState state) {
        if (state.recurrence == Recurrence.ONCE) return texts.text(R.string.rhythm_once);
        if (state.recurrence == Recurrence.DAILY) return texts.text(R.string.rhythm_daily);
        if (state.recurrence == Recurrence.INTERVAL)
            return texts.text(R.string.rhythm_every_n_value, state.intervalDays);
        return days(state.weekdayMask);
    }

    public String time(EditorUiState state) {
        if (state.recurrence == Recurrence.ONCE) return empty();
        int[] names = {R.string.tod_morning, R.string.tod_noon,
                R.string.tod_evening, R.string.tod_night};
        List<String> values = new ArrayList<>();
        for (int index = 0; index < TimeOfDay.values().length; index++)
            if ((state.timeOfDayMask & TimeOfDay.values()[index].bit) != 0)
                values.add(texts.text(names[index]));
        return join(values);
    }

    public String duration(EditorUiState state) {
        return state.estimatedMinutes == null ? empty()
                : texts.text(R.string.editor_duration_minutes, state.estimatedMinutes);
    }

    public String bound(EditorUiState state) {
        if (state.recurrence == Recurrence.ONCE) return state.deadlineOn == null
                ? texts.text(R.string.deadline_none)
                : texts.text(R.string.bound_until_value, date.format(state.deadlineOn));
        if (state.boundKind == TaskBoundKind.FOREVER) return texts.text(R.string.bound_forever);
        if (state.boundKind == TaskBoundKind.UNTIL_DATE)
            return texts.text(R.string.bound_until_value, date.format(state.boundUntilOn));
        if (state.boundKind == TaskBoundKind.FOR_WEEKS)
            return texts.text(R.string.bound_weeks_value, state.boundWeeks,
                    date.format(state.boundUntilOn));
        return texts.text(R.string.bound_times_value, state.remainingCount);
    }

    public String steps(EditorUiState state) {
        if (state.stepStates.isEmpty()) return empty();
        List<String> values = new ArrayList<>();
        for (EditorStepState step : state.stepStates) values.add(step.text);
        return join(values);
    }

    public String stepMeta(EditorStepState step) {
        List<String> values = new ArrayList<>();
        if (step.cadenceMode == StepCadenceMode.WEEKDAYS) values.add(days(step.weekdayMask));
        else if (step.cadenceMode == StepCadenceMode.INTERVAL && step.intervalDays != null)
            values.add(texts.text(R.string.rhythm_every_n_value, step.intervalDays));
        String amount = stepFormatter.format(step.amount, "");
        if (!amount.isEmpty()) values.add(amount);
        return join(values);
    }

    public String empty() { return texts.text(R.string.editor_summary_empty); }
    public String date(java.time.LocalDate value) { return date.format(value); }

    private String days(int mask) {
        int[] names = {R.string.day_mon, R.string.day_tue, R.string.day_wed,
                R.string.day_thu, R.string.day_fri, R.string.day_sat, R.string.day_sun};
        List<String> values = new ArrayList<>();
        for (int index = 0; index < names.length; index++)
            if ((mask & 1 << index) != 0) values.add(texts.text(names[index]));
        return join(values);
    }

    private String join(String first, String second) {
        return texts.text(R.string.editor_summary_join, first, second);
    }

    private String join(List<String> values) {
        if (values.isEmpty()) return "";
        String result = values.get(0);
        for (int index = 1; index < values.size(); index++) result = join(result, values.get(index));
        return result;
    }
}
