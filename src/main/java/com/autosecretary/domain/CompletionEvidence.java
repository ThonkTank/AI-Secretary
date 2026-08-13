package com.autosecretary.domain;

import java.time.LocalDateTime;

public record CompletionEvidence(String workItemId, LocalDateTime completedAt) { }
