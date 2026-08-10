package com.autosecretary.core;

import java.time.LocalDateTime;

/** Immutable behavioral evidence. Planning learns only from facts recorded here. */
public record Completion(String id, String obligationId, LocalDateTime completedAt) {
}
