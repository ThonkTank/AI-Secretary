package com.secretary.features.motivation.domain

import com.secretary.features.statistics.domain.model.TaskStatistics

/**
 * MotivationalMessageService - Domain Service for Motivational Messages
 * Phase 4: Motivation & Statistics
 *
 * Pure domain service with NO Android dependencies.
 * Generates contextual motivational messages based on statistics.
 *
 * Message Priority (evaluated in order):
 * 1. Streak-based messages (highest priority)
 * 2. Completion-based messages
 * 3. Activity-based messages
 * 4. Default message
 */
class MotivationalMessageService {

    /**
     * Generate motivational message based on current statistics.
     *
     * @param stats Current task statistics
     * @return Motivational message string
     */
    fun getMessage(stats: TaskStatistics): String {
        return when {
            // Streak-basierte Messages (höchste Priorität)
            stats.highestActiveStreak >= 30 -> "Legendär! 30+ Tage Streak!"
            stats.highestActiveStreak >= 14 -> "Zwei Wochen stark!"
            stats.highestActiveStreak >= 7 -> "Eine Woche! Weiter so!"
            stats.highestActiveStreak >= 3 -> "Guter Streak! Nicht aufhören!"

            // Completion-basierte Messages
            stats.completedToday >= 5 -> "Unglaublich! ${stats.completedToday} Tasks heute!"
            stats.completedToday >= 3 -> "Toller Fortschritt heute!"
            stats.completedToday >= 1 -> "Guter Start! Weiter so!"

            // Aktivitäts-basierte Messages
            stats.activeTasks == 0 && stats.totalTasks > 0 -> "Alles erledigt! Gut gemacht!"
            stats.activeTasks <= 3 && stats.activeTasks > 0 -> "Fast geschafft! Nur noch ${stats.activeTasks}!"

            // Default
            else -> "Lass uns produktiv sein!"
        }
    }
}
