package com.secretary.features.dreams.domain.service

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for XPCalculationService.
 * Tests XP calculation formulas for task completion.
 */
class XPCalculationServiceTest {

    private lateinit var service: XPCalculationService

    @Before
    fun setup() {
        service = XPCalculationService()
    }

    // ========== calculateBaseXP Tests ==========

    @Test
    fun `calculateBaseXP returns 10 for priority 0 (Low)`() {
        assertEquals(10L, service.calculateBaseXP(0))
    }

    @Test
    fun `calculateBaseXP returns 25 for priority 1 (Medium)`() {
        assertEquals(25L, service.calculateBaseXP(1))
    }

    @Test
    fun `calculateBaseXP returns 50 for priority 2 (High)`() {
        assertEquals(50L, service.calculateBaseXP(2))
    }

    @Test
    fun `calculateBaseXP returns 100 for priority 3 (Urgent)`() {
        assertEquals(100L, service.calculateBaseXP(3))
    }

    @Test
    fun `calculateBaseXP returns default 10 for invalid priority`() {
        assertEquals(10L, service.calculateBaseXP(-1))
        assertEquals(10L, service.calculateBaseXP(5))
        assertEquals(10L, service.calculateBaseXP(100))
    }

    // ========== calculateTotalXP Tests ==========

    @Test
    fun `calculateTotalXP multiplies base by milestone count`() {
        // High priority (50) × 3 milestones = 150
        assertEquals(150L, service.calculateTotalXP(2, 3))
    }

    @Test
    fun `calculateTotalXP returns 0 for zero milestones`() {
        assertEquals(0L, service.calculateTotalXP(3, 0))
    }

    @Test
    fun `calculateTotalXP handles single milestone correctly`() {
        assertEquals(100L, service.calculateTotalXP(3, 1))
    }

    // ========== calculateXPPerMilestone Tests ==========

    @Test
    fun `calculateXPPerMilestone returns full base XP (no splitting)`() {
        // Key requirement: each milestone gets FULL XP, not split
        assertEquals(50L, service.calculateXPPerMilestone(2))
        assertEquals(100L, service.calculateXPPerMilestone(3))
    }

    // ========== Edge Cases ==========

    @Test
    fun `XP values are always positive`() {
        for (priority in -10..10) {
            assertTrue(service.calculateBaseXP(priority) > 0)
        }
    }

    @Test
    fun `high milestone count does not overflow`() {
        val result = service.calculateTotalXP(3, 1000)
        assertEquals(100_000L, result)
    }
}
