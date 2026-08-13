package com.autosecretary.application;

import java.time.LocalDateTime;

public record StepCompletion(String stepId, String occurrenceKey, LocalDateTime completedAt) { }
