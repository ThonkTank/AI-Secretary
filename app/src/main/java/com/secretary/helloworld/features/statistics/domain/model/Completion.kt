package com.secretary.helloworld.features.statistics.domain.model

/**
 * Domain model for a task completion.
 * Phase 4.5.3 Wave 10 Step 5: Completion Repository Pattern
 *
 * Pure domain model with NO Room or Android dependencies.
 * Used by use cases and presentation layer.
 */
data class Completion(
    val completionId: Long,
    val taskId: Long,
    val completedAt: Long,
    val timeSpentMinutes: Int,
    val difficulty: Int, // 0-10 scale
    val notes: String?
)
