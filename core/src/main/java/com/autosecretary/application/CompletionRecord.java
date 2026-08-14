package com.autosecretary.application;

import java.time.LocalDateTime;

public record CompletionRecord(String id, String workItemId, String occurrenceKey, LocalDateTime completedAt) { }
