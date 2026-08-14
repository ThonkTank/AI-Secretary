package com.autosecretary.application;

import com.autosecretary.domain.BusyInterval;

import java.time.LocalDate;
import java.util.List;

public interface CalendarPort {
    List<BusyInterval> read(LocalDate fromInclusive, LocalDate toExclusive);
}
