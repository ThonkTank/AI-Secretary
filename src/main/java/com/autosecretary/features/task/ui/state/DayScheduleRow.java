package com.autosecretary.features.task.ui.state;

import com.autosecretary.shared.ui.UiStateCarrier;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record DayScheduleRow(
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime) implements UiStateCarrier {
}
