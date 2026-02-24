package com.autosecretary.features.task.domain;

import java.time.LocalDateTime;

public record TimeWindow(LocalDateTime start, LocalDateTime end) {
}
