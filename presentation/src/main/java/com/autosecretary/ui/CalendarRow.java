package com.autosecretary.ui;

import java.time.LocalDateTime;

public record CalendarRow(LocalDateTime start, LocalDateTime end, String title) { }
