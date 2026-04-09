package com.autosecretary.features.task.domain.model;

import java.time.Instant;

/**
 * Typed completion telemetry event used for persistence, inference, and replay-safe ingestion.
 *
 * <h2>Why this exists</h2>
 * Replaces weak string-based history contracts with stable, queryable event shape including
 * idempotency and source metadata.
 */
public record CompletionEvent(String eventId,
                              String taskId,
                              Instant finishedAtUtc,
                              String source,
                              String idempotencyKey,
                              Instant ingestedAtUtc) {
}
