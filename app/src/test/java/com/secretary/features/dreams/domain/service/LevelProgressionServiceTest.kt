package com.secretary.features.dreams.domain.service

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for LevelProgressionService.
 * Tests level progression calculations.
 * Formula: XP_required = 500 * (level - 1)²
 */
class LevelProgressionServiceTest {

    private lateinit var service: LevelProgressionService

    @Before
    fun setup() {
        service = LevelProgressionService()
    }

    // ========== calculateLevel Tests ==========

    @Test
    fun `calculateLevel returns 1 for 0 XP`() {
        assertEquals(1, service.calculateLevel(0))
    }

    @Test
    fun `calculateLevel returns 1 for XP below 500`() {
        assertEquals(1, service.calculateLevel(499))
    }

    @Test
    fun `calculateLevel returns 2 for 500 XP`() {
        assertEquals(2, service.calculateLevel(500))
    }

    @Test
    fun `calculateLevel returns 3 for 2000 XP`() {
        // Level 3 requires: 500 * 2² = 2000 XP
        assertEquals(3, service.calculateLevel(2000))
    }

    @Test
    fun `calculateLevel returns 5 for 8000 XP`() {
        // Level 5 requires: 500 * 4² = 8000 XP
        assertEquals(5, service.calculateLevel(8000))
    }

    @Test
    fun `calculateLevel returns 10 for 40500 XP`() {
        // Level 10 requires: 500 * 9² = 40500 XP
        assertEquals(10, service.calculateLevel(40500))
    }

    // ========== getXPForLevel Tests ==========

    @Test
    fun `getXPForLevel returns 0 for level 1`() {
        assertEquals(0L, service.getXPForLevel(1))
    }

    @Test
    fun `getXPForLevel returns 500 for level 2`() {
        assertEquals(500L, service.getXPForLevel(2))
    }

    @Test
    fun `getXPForLevel returns 2000 for level 3`() {
        assertEquals(2000L, service.getXPForLevel(3))
    }

    @Test
    fun `getXPForLevel returns correct values for levels 1-10`() {
        val expected = mapOf(
            1 to 0L,
            2 to 500L,
            3 to 2000L,
            4 to 4500L,
            5 to 8000L,
            6 to 12500L,
            7 to 18000L,
            8 to 24500L,
            9 to 32000L,
            10 to 40500L
        )
        expected.forEach { (level, xp) ->
            assertEquals("Level $level", xp, service.getXPForLevel(level))
        }
    }

    // ========== getLevelProgress Tests ==========

    @Test
    fun `getLevelProgress returns 0 for start of level`() {
        assertEquals(0f, service.getLevelProgress(0), 0.01f)
        assertEquals(0f, service.getLevelProgress(500), 0.01f)
    }

    @Test
    fun `getLevelProgress returns 0_5 for halfway through level`() {
        // Level 2: 500-2000, halfway = 1250
        val progress = service.getLevelProgress(1250)
        assertEquals(0.5f, progress, 0.01f)
    }

    // ========== getXPToNextLevel Tests ==========

    @Test
    fun `getXPToNextLevel returns 500 for 0 XP`() {
        assertEquals(500L, service.getXPToNextLevel(0))
    }

    @Test
    fun `getXPToNextLevel returns correct value mid-level`() {
        // At 300 XP, need 200 more to reach 500 (level 2)
        assertEquals(200L, service.getXPToNextLevel(300))
    }

    // ========== checkLevelUp Tests ==========

    @Test
    fun `checkLevelUp detects single level up`() {
        val result = service.checkLevelUp(400, 600)
        assertTrue(result.leveledUp)
        assertEquals(1, result.previousLevel)
        assertEquals(2, result.newLevel)
        assertEquals(1, result.levelsGained)
    }

    @Test
    fun `checkLevelUp detects multiple level ups`() {
        val result = service.checkLevelUp(400, 8500)
        assertTrue(result.leveledUp)
        assertEquals(1, result.previousLevel)
        assertEquals(5, result.newLevel)
        assertEquals(4, result.levelsGained)
    }

    @Test
    fun `checkLevelUp returns false when no level change`() {
        val result = service.checkLevelUp(100, 300)
        assertFalse(result.leveledUp)
        assertEquals(1, result.previousLevel)
        assertEquals(1, result.newLevel)
    }

    // ========== Edge Cases ==========

    @Test
    fun `handles negative XP gracefully`() {
        assertEquals(1, service.calculateLevel(-100))
    }

    @Test
    fun `handles very high XP values`() {
        val level = service.calculateLevel(1_000_000)
        assertTrue(level > 40)
    }
}
