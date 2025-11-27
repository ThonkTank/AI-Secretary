package com.secretary.features.dreams.domain.model

/**
 * Dream domain model.
 * Dream-to-Task Feature - Domain Layer
 *
 * Represents a long-term goal or aspiration in the business logic layer.
 * Dreams track XP progression and level advancement.
 */
data class Dream(
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val iconName: String? = null,
    val color: Int = 0xFF6200EE.toInt(),
    val totalXp: Long = 0,
    val currentLevel: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
) {
    /**
     * Get level string with title (e.g., "Lvl 5 Learn Guitar")
     */
    fun getLevelString(): String = "Lvl $currentLevel $title"

    /**
     * Calculate XP needed for next level
     * Uses exponential scaling: level^2 * 100
     */
    fun getXpForNextLevel(): Long {
        val nextLevel = currentLevel + 1
        return nextLevel * nextLevel * 100L
    }

    /**
     * Calculate progress percentage to next level
     */
    fun getProgressPercentage(): Float {
        val currentLevelXp = currentLevel * currentLevel * 100L
        val nextLevelXp = getXpForNextLevel()
        val xpInCurrentLevel = totalXp - currentLevelXp
        val xpNeededForLevel = nextLevelXp - currentLevelXp

        if (xpNeededForLevel <= 0) return 100f

        return ((xpInCurrentLevel.toFloat() / xpNeededForLevel.toFloat()) * 100f).coerceIn(0f, 100f)
    }

    /**
     * Check if dream has enough XP to level up
     */
    fun canLevelUp(): Boolean = totalXp >= getXpForNextLevel()

    /**
     * String representation showing title and level
     */
    override fun toString(): String = getLevelString()
}
