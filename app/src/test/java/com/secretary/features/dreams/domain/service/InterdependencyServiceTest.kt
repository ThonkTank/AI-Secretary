package com.secretary.features.dreams.domain.service

import com.secretary.features.dreams.domain.model.MilestoneInterdependency
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for InterdependencyService.
 * Tests validation, impact calculations, and interdependency analysis.
 *
 * Dream-to-Task Feature - Test Suite
 */
class InterdependencyServiceTest {

    private lateinit var service: InterdependencyService

    @Before
    fun setup() {
        service = InterdependencyService()
    }

    // ========== validateRating Tests ==========

    @Test
    fun `validateRating returns true for valid rating in range`() {
        // Test valid ratings across the spectrum
        assertTrue(service.validateRating(-5))
        assertTrue(service.validateRating(-3))
        assertTrue(service.validateRating(0))
        assertTrue(service.validateRating(3))
        assertTrue(service.validateRating(5))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validateRating throws exception for rating below MIN`() {
        service.validateRating(-6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validateRating throws exception for rating above MAX`() {
        service.validateRating(6)
    }

    @Test
    fun `validateRating accepts boundary values`() {
        // Test exact boundary conditions
        assertTrue(service.validateRating(-5)) // MIN_RATING
        assertTrue(service.validateRating(5))  // MAX_RATING
    }

    // ========== calculateNetImpact Tests ==========

    @Test
    fun `calculateNetImpact returns sum of all ratings`() {
        val dependencies = listOf(
            createInterdependency(1L, 2L, 3),  // +3
            createInterdependency(1L, 3L, -2), // -2
            createInterdependency(1L, 4L, 5),  // +5
            createInterdependency(1L, 5L, -1)  // -1
        )
        // Total: 3 + (-2) + 5 + (-1) = 5
        assertEquals(5, service.calculateNetImpact(dependencies))
    }

    @Test
    fun `calculateNetImpact returns 0 for empty list`() {
        assertEquals(0, service.calculateNetImpact(emptyList()))
    }

    // ========== getImpactDescription Tests ==========

    @Test
    fun `getImpactDescription returns correct string for positive ratings`() {
        assertEquals("Extremely Positive - Essential synergy", service.getImpactDescription(5))
        assertEquals("Strongly Positive - Major synergy", service.getImpactDescription(4))
        assertEquals("Strongly Positive - Major synergy", service.getImpactDescription(3))
    }

    @Test
    fun `getImpactDescription returns correct string for negative ratings`() {
        assertEquals("Extremely Negative - Critical conflict", service.getImpactDescription(-5))
        assertEquals("Strongly Negative - Major conflict", service.getImpactDescription(-4))
        assertEquals("Strongly Negative - Major conflict", service.getImpactDescription(-3))
    }

    @Test
    fun `getImpactDescription returns Neutral for zero`() {
        assertEquals("Neutral - No significant impact", service.getImpactDescription(0))
    }

    // ========== findSynergies and findConflicts Tests ==========

    @Test
    fun `findSynergies returns only interdependencies with rating greater than or equal to 3`() {
        val dependencies = listOf(
            createInterdependency(1L, 2L, 5),  // Synergy
            createInterdependency(1L, 3L, 3),  // Synergy (boundary)
            createInterdependency(1L, 4L, 2),  // Not synergy
            createInterdependency(1L, 5L, -3), // Conflict
            createInterdependency(1L, 6L, 0)   // Neutral
        )

        val synergies = service.findSynergies(dependencies)

        assertEquals(2, synergies.size)
        assertTrue(synergies.all { it.impactRating >= 3 })
        assertTrue(synergies.any { it.targetMilestoneId == 2L && it.impactRating == 5 })
        assertTrue(synergies.any { it.targetMilestoneId == 3L && it.impactRating == 3 })
    }

    @Test
    fun `findConflicts returns only interdependencies with rating less than or equal to minus 3`() {
        val dependencies = listOf(
            createInterdependency(1L, 2L, 5),   // Synergy
            createInterdependency(1L, 3L, -3),  // Conflict (boundary)
            createInterdependency(1L, 4L, -2),  // Not conflict
            createInterdependency(1L, 5L, -5),  // Conflict
            createInterdependency(1L, 6L, 0)    // Neutral
        )

        val conflicts = service.findConflicts(dependencies)

        assertEquals(2, conflicts.size)
        assertTrue(conflicts.all { it.impactRating <= -3 })
        assertTrue(conflicts.any { it.targetMilestoneId == 3L && it.impactRating == -3 })
        assertTrue(conflicts.any { it.targetMilestoneId == 5L && it.impactRating == -5 })
    }

    // ========== analyzeImpactDistribution Tests ==========

    @Test
    fun `analyzeImpactDistribution calculates correct counts and percentages`() {
        val dependencies = listOf(
            createInterdependency(1L, 2L, 3),   // Positive
            createInterdependency(1L, 3L, 5),   // Positive
            createInterdependency(1L, 4L, -2),  // Negative
            createInterdependency(1L, 5L, -4),  // Negative
            createInterdependency(1L, 6L, -1),  // Negative
            createInterdependency(1L, 7L, 0),   // Neutral
            createInterdependency(1L, 8L, 0),   // Neutral
            createInterdependency(1L, 9L, 1),   // Positive
            createInterdependency(1L, 10L, 2),  // Positive
            createInterdependency(1L, 11L, 0)   // Neutral
        )
        // Total: 10 dependencies
        // Positive: 4 (3, 5, 1, 2) = 40%
        // Negative: 3 (-2, -4, -1) = 30%
        // Neutral: 3 (0, 0, 0) = 30%

        val distribution = service.analyzeImpactDistribution(dependencies)

        assertEquals(4, distribution.positiveCount)
        assertEquals(3, distribution.negativeCount)
        assertEquals(3, distribution.neutralCount)

        assertEquals(40f, distribution.positivePercentage, 0.01f)
        assertEquals(30f, distribution.negativePercentage, 0.01f)
        assertEquals(30f, distribution.neutralPercentage, 0.01f)
    }

    @Test
    fun `analyzeImpactDistribution returns zeros for empty list`() {
        val distribution = service.analyzeImpactDistribution(emptyList())

        assertEquals(0, distribution.positiveCount)
        assertEquals(0, distribution.negativeCount)
        assertEquals(0, distribution.neutralCount)
        assertEquals(0f, distribution.positivePercentage, 0.01f)
        assertEquals(0f, distribution.negativePercentage, 0.01f)
        assertEquals(0f, distribution.neutralPercentage, 0.01f)
    }

    @Test
    fun `analyzeImpactDistribution handles all positive interdependencies`() {
        val dependencies = listOf(
            createInterdependency(1L, 2L, 1),
            createInterdependency(1L, 3L, 3),
            createInterdependency(1L, 4L, 5)
        )

        val distribution = service.analyzeImpactDistribution(dependencies)

        assertEquals(3, distribution.positiveCount)
        assertEquals(0, distribution.negativeCount)
        assertEquals(0, distribution.neutralCount)
        assertEquals(100f, distribution.positivePercentage, 0.01f)
        assertEquals(0f, distribution.negativePercentage, 0.01f)
        assertEquals(0f, distribution.neutralPercentage, 0.01f)
    }

    @Test
    fun `analyzeImpactDistribution handles all negative interdependencies`() {
        val dependencies = listOf(
            createInterdependency(1L, 2L, -1),
            createInterdependency(1L, 3L, -3),
            createInterdependency(1L, 4L, -5)
        )

        val distribution = service.analyzeImpactDistribution(dependencies)

        assertEquals(0, distribution.positiveCount)
        assertEquals(3, distribution.negativeCount)
        assertEquals(0, distribution.neutralCount)
        assertEquals(0f, distribution.positivePercentage, 0.01f)
        assertEquals(100f, distribution.negativePercentage, 0.01f)
        assertEquals(0f, distribution.neutralPercentage, 0.01f)
    }

    @Test
    fun `analyzeImpactDistribution handles all neutral interdependencies`() {
        val dependencies = listOf(
            createInterdependency(1L, 2L, 0),
            createInterdependency(1L, 3L, 0)
        )

        val distribution = service.analyzeImpactDistribution(dependencies)

        assertEquals(0, distribution.positiveCount)
        assertEquals(0, distribution.negativeCount)
        assertEquals(2, distribution.neutralCount)
        assertEquals(0f, distribution.positivePercentage, 0.01f)
        assertEquals(0f, distribution.negativePercentage, 0.01f)
        assertEquals(100f, distribution.neutralPercentage, 0.01f)
    }

    // ========== Edge Cases ==========

    @Test
    fun `findSynergies returns empty list when no synergies exist`() {
        val dependencies = listOf(
            createInterdependency(1L, 2L, 2),
            createInterdependency(1L, 3L, -1),
            createInterdependency(1L, 4L, 0)
        )

        assertTrue(service.findSynergies(dependencies).isEmpty())
    }

    @Test
    fun `findConflicts returns empty list when no conflicts exist`() {
        val dependencies = listOf(
            createInterdependency(1L, 2L, 2),
            createInterdependency(1L, 3L, -1),
            createInterdependency(1L, 4L, 0)
        )

        assertTrue(service.findConflicts(dependencies).isEmpty())
    }

    @Test
    fun `calculateNetImpact handles large positive sum`() {
        val dependencies = List(20) { index ->
            createInterdependency(1L, index.toLong() + 2, 5)
        }
        // 20 × 5 = 100
        assertEquals(100, service.calculateNetImpact(dependencies))
    }

    @Test
    fun `calculateNetImpact handles large negative sum`() {
        val dependencies = List(20) { index ->
            createInterdependency(1L, index.toLong() + 2, -5)
        }
        // 20 × -5 = -100
        assertEquals(-100, service.calculateNetImpact(dependencies))
    }

    @Test
    fun `getImpactDescription covers all rating ranges`() {
        // Test all possible ratings from -5 to 5
        for (rating in -5..5) {
            val description = service.getImpactDescription(rating)
            assertNotNull(description)
            assertTrue(description.isNotEmpty())
        }
    }

    // ========== Helper Methods ==========

    /**
     * Helper method to create test MilestoneInterdependency instances
     */
    private fun createInterdependency(
        sourceId: Long,
        targetId: Long,
        rating: Int
    ): MilestoneInterdependency {
        return MilestoneInterdependency(
            sourceMilestoneId = sourceId,
            targetMilestoneId = targetId,
            impactRating = rating,
            description = "Test interdependency",
            createdAt = System.currentTimeMillis()
        )
    }
}
