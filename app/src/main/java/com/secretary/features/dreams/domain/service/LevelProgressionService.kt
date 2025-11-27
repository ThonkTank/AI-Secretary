package com.secretary.features.dreams.domain.service

import kotlin.math.sqrt

/**
 * Handles dream level progression calculations.
 * Dream-to-Task Feature - Domain Layer Service
 *
 * This service manages the level system for dreams based on accumulated XP.
 * Uses exponential scaling to make higher levels progressively harder to achieve.
 *
 * Level Formula: XP_required = 500 * (level - 1)²
 *
 * Example level thresholds (cumulative XP):
 * - Level 1: 0 XP (starting level)
 * - Level 2: 500 XP
 * - Level 3: 2,000 XP
 * - Level 4: 4,500 XP
 * - Level 5: 8,000 XP
 * - Level 10: 40,500 XP
 * - Level 20: 180,500 XP
 */
class LevelProgressionService {

    companion object {
        /**
         * Base multiplier for XP calculation formula.
         * Formula: XP = BASE_XP_MULTIPLIER * (level - 1)²
         */
        private const val BASE_XP_MULTIPLIER = 500L

        /**
         * Minimum level (starting level for all dreams)
         */
        private const val MIN_LEVEL = 1
    }

    /**
     * Calculate the current level based on total accumulated XP.
     *
     * Uses inverse of the level formula to find the highest level achieved.
     * Formula: level = floor(sqrt(totalXP / 500)) + 1
     *
     * Examples:
     * - 0 XP → Level 1
     * - 500 XP → Level 2
     * - 2,000 XP → Level 3
     * - 4,500 XP → Level 4
     * - 8,000 XP → Level 5
     *
     * @param totalXP Total accumulated XP
     * @return Current level (minimum 1)
     */
    fun calculateLevel(totalXP: Long): Int {
        if (totalXP <= 0) return MIN_LEVEL

        // Inverse formula: level = sqrt(totalXP / 500) + 1
        val level = sqrt(totalXP.toDouble() / BASE_XP_MULTIPLIER).toInt() + 1
        return level.coerceAtLeast(MIN_LEVEL)
    }

    /**
     * Get the cumulative XP threshold required to reach a specific level.
     *
     * Formula: XP_threshold = 500 * (level - 1)²
     *
     * Examples:
     * - Level 1: 0 XP
     * - Level 2: 500 XP
     * - Level 3: 2,000 XP
     * - Level 5: 8,000 XP
     * - Level 10: 40,500 XP
     *
     * @param level Target level
     * @return Cumulative XP needed to reach that level
     */
    fun getXPForLevel(level: Int): Long {
        if (level <= MIN_LEVEL) return 0L

        val levelDiff = (level - 1).toLong()
        return BASE_XP_MULTIPLIER * levelDiff * levelDiff
    }

    /**
     * Calculate progress within the current level as a fraction.
     *
     * Returns a value between 0.0 (just reached current level) and
     * 1.0 (about to reach next level).
     *
     * Formula:
     * progress = (totalXP - currentLevelXP) / (nextLevelXP - currentLevelXP)
     *
     * Example with 6,000 XP (Level 4):
     * - Current level threshold: 4,500 XP
     * - Next level threshold: 8,000 XP
     * - XP in level: 6,000 - 4,500 = 1,500
     * - XP needed for level: 8,000 - 4,500 = 3,500
     * - Progress: 1,500 / 3,500 = 0.43 (43%)
     *
     * @param totalXP Total accumulated XP
     * @return Progress fraction (0.0 to 1.0)
     */
    fun getLevelProgress(totalXP: Long): Float {
        val currentLevel = calculateLevel(totalXP)
        val currentLevelXP = getXPForLevel(currentLevel)
        val nextLevelXP = getXPForLevel(currentLevel + 1)

        val xpInCurrentLevel = totalXP - currentLevelXP
        val xpNeededForLevel = nextLevelXP - currentLevelXP

        if (xpNeededForLevel <= 0) return 0f

        return (xpInCurrentLevel.toFloat() / xpNeededForLevel.toFloat())
            .coerceIn(0f, 1f)
    }

    /**
     * Calculate how much XP is needed to reach the next level.
     *
     * Formula: XP_needed = nextLevelThreshold - currentXP
     *
     * Example with 6,000 XP (Level 4):
     * - Next level (5) requires: 8,000 XP
     * - XP needed: 8,000 - 6,000 = 2,000 XP
     *
     * @param totalXP Total accumulated XP
     * @return XP needed to reach next level (0 if already at threshold)
     */
    fun getXPToNextLevel(totalXP: Long): Long {
        val currentLevel = calculateLevel(totalXP)
        val nextLevelXP = getXPForLevel(currentLevel + 1)
        val xpNeeded = nextLevelXP - totalXP

        return xpNeeded.coerceAtLeast(0L)
    }

    /**
     * Check if a level up occurred between two XP values.
     *
     * Compares the levels before and after XP gain to detect level ups.
     * Handles multiple level gains in a single XP award.
     *
     * Example scenarios:
     * - 450 XP → 550 XP: Level 1 → Level 2 (1 level gained)
     * - 400 XP → 2,500 XP: Level 1 → Level 3 (2 levels gained)
     * - 1,000 XP → 1,500 XP: Level 2 → Level 2 (no level up)
     *
     * @param previousXP XP before the gain
     * @param newXP XP after the gain
     * @return LevelUpResult containing level up information
     */
    fun checkLevelUp(previousXP: Long, newXP: Long): LevelUpResult {
        val previousLevel = calculateLevel(previousXP)
        val newLevel = calculateLevel(newXP)
        val leveledUp = newLevel > previousLevel

        return LevelUpResult(
            leveledUp = leveledUp,
            previousLevel = previousLevel,
            newLevel = newLevel,
            levelsGained = (newLevel - previousLevel).coerceAtLeast(0)
        )
    }
}

/**
 * Result of a level up check.
 *
 * Contains information about whether a level up occurred and details
 * about the level progression.
 *
 * @property leveledUp True if at least one level was gained
 * @property previousLevel Level before XP gain
 * @property newLevel Level after XP gain
 * @property levelsGained Number of levels gained (newLevel - previousLevel)
 */
data class LevelUpResult(
    val leveledUp: Boolean,
    val previousLevel: Int,
    val newLevel: Int,
    val levelsGained: Int = newLevel - previousLevel
)
