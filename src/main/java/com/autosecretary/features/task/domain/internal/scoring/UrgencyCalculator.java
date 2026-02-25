package com.autosecretary.features.task.domain.internal.scoring;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.domain.internal.scoring.ScoringModel.UrgencyState;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class UrgencyCalculator {

    public UrgencyState computeState(Task task, LocalDate day) {
        TaskCore.Repetition rep = task.core.repetition;
        double remainingDays;
        if (task.core.deadline != null) {
            remainingDays = (double) ChronoUnit.DAYS.between(day, task.core.deadline);
        } else if (rep != null && rep.reps > 0 && rep.periodUnit != null) {
            LocalDate periodEnd = rep.periodEnd();
            remainingDays = periodEnd != null
                    ? (double) ChronoUnit.DAYS.between(day, periodEnd)
                    : rep.periodInDays();
        } else {
            remainingDays = 1;
        }

        double requiredDays = task.requiredDays();
        boolean deadlineExpired = task.core.closeOnMiss && task.core.deadline != null && day.isAfter(task.core.deadline);
        return new UrgencyState(remainingDays, requiredDays, deadlineExpired);
    }

    public int applyMultiplier(int score, Task task, UrgencyState urgencyState) {
        double urgency;
        if (urgencyState.remainingDays() <= 0) {
            urgency = 100;
        } else if (task.core.deadline != null || (task.core.repetition != null && task.core.repetition.reps > 0)) {
            urgency = 1.0 + urgencyState.requiredDays() / urgencyState.remainingDays();
        } else {
            urgency = 1.0;
        }
        return (int) (score * urgency);
    }
}
