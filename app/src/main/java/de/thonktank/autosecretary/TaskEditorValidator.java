package de.thonktank.autosecretary;

import de.thonktank.autosecretary.domain.model.Recurrence;

public final class TaskEditorValidator {
    public enum Error { NONE, TITLE, WEEKDAYS, CONDITION }

    public Error validate(EditorUiState draft) {
        if (draft.title == null || draft.title.trim().isEmpty()) return Error.TITLE;
        if (draft.recurrence == Recurrence.WEEKDAYS
                && !ScheduleCalculator.hasWeekday(draft.weekdayMask)) return Error.WEEKDAYS;
        if (draft.ongoing && (draft.condition == null || draft.condition.trim().isEmpty()))
            return Error.CONDITION;
        return Error.NONE;
    }
}
