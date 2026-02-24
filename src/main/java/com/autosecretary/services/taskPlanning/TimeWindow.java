package com.autosecretary.services.taskPlanning;

import java.time.LocalDateTime;

public record TimeWindow(LocalDateTime start, LocalDateTime end) {
}
