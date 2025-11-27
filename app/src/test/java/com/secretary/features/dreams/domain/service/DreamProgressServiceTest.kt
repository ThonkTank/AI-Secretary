package com.secretary.features.dreams.domain.service

import com.secretary.features.dreams.domain.model.Dream
import com.secretary.features.dreams.domain.model.Milestone
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for DreamProgressService.
 * Tests progress calculation and SOLL-IST analysis.
 */
class DreamProgressServiceTest {

    private lateinit var service: DreamProgressService

    @Before
    fun setup() {
        service = DreamProgressService()
    }

    // ========== Test Data ==========

    private fun createMilestone(
        id: Long,
        title: String,
        currentXp: Long = 0,
        targetXp: Long = 1000,
        status: Milestone.Status = Milestone.Status.ACTIVE
    ) = Milestone(
        id = id,
        dreamId = 1,
        title = title,
        description = null,
        targetXp = targetXp,
        currentXp = currentXp,
        status = status,
        createdAt = System.currentTimeMillis(),
        completedAt = null,
        orderIndex = 0
    )

    private fun createDream(
        totalXp: Long = 0,
        currentLevel: Int = 1
    ) = Dream(
        id = 1,
        title = "Test Dream",
        description = null,
        iconName = null,
        color = 0xFF6200EE.toInt(),
        totalXp = totalXp,
        currentLevel = currentLevel,
        createdAt = System.currentTimeMillis(),
        isArchived = false
    )

    // ========== calculateDreamProgress Tests ==========

    @Test
    fun `calculateDreamProgress returns 0 for no milestones`() {
        val progress = service.calculateDreamProgress(emptyList())
        assertEquals(0f, progress, 0.01f)
    }

    @Test
    fun `calculateDreamProgress returns 1 when all milestones completed`() {
        val milestones = listOf(
            createMilestone(1, "M1", status = Milestone.Status.COMPLETED),
            createMilestone(2, "M2", status = Milestone.Status.COMPLETED)
        )
        val progress = service.calculateDreamProgress(milestones)
        assertEquals(1f, progress, 0.01f)
    }

    @Test
    fun `calculateDreamProgress returns 0_5 when half milestones completed`() {
        val milestones = listOf(
            createMilestone(1, "M1", status = Milestone.Status.COMPLETED),
            createMilestone(2, "M2", status = Milestone.Status.ACTIVE)
        )
        val progress = service.calculateDreamProgress(milestones)
        assertEquals(0.5f, progress, 0.01f)
    }

    // ========== calculateMilestoneProgress Tests ==========

    @Test
    fun `calculateMilestoneProgress returns 0 for 0 XP`() {
        val milestone = createMilestone(1, "Test", currentXp = 0, targetXp = 1000)
        val progress = service.calculateMilestoneProgress(milestone)
        assertEquals(0f, progress, 0.01f)
    }

    @Test
    fun `calculateMilestoneProgress returns 0_5 for halfway XP`() {
        val milestone = createMilestone(1, "Test", currentXp = 500, targetXp = 1000)
        val progress = service.calculateMilestoneProgress(milestone)
        assertEquals(0.5f, progress, 0.01f)
    }

    @Test
    fun `calculateMilestoneProgress returns 1 for full XP`() {
        val milestone = createMilestone(1, "Test", currentXp = 1000, targetXp = 1000)
        val progress = service.calculateMilestoneProgress(milestone)
        assertEquals(1f, progress, 0.01f)
    }

    @Test
    fun `calculateMilestoneProgress handles overflow gracefully`() {
        val milestone = createMilestone(1, "Test", currentXp = 1500, targetXp = 1000)
        val progress = service.calculateMilestoneProgress(milestone)
        assertTrue(progress >= 1f)
    }

    // ========== calculateProgressGap Tests ==========

    @Test
    fun `calculateProgressGap returns 0 when on track`() {
        val gap = service.calculateProgressGap(0.5f, 0.5f)
        assertEquals(0f, gap, 0.01f)
    }

    @Test
    fun `calculateProgressGap returns positive when ahead`() {
        val gap = service.calculateProgressGap(0.3f, 0.5f)
        assertTrue(gap > 0)
    }

    @Test
    fun `calculateProgressGap returns negative when behind`() {
        val gap = service.calculateProgressGap(0.7f, 0.3f)
        assertTrue(gap < 0)
    }

    // ========== getDreamStatistics Tests ==========

    @Test
    fun `getDreamStatistics calculates correct totals`() {
        val dream = createDream(totalXp = 1000)
        val milestones = listOf(
            createMilestone(1, "M1", currentXp = 1000, targetXp = 1000, status = Milestone.Status.COMPLETED),
            createMilestone(2, "M2", currentXp = 200, targetXp = 1000, status = Milestone.Status.ACTIVE),
            createMilestone(3, "M3", currentXp = 0, targetXp = 1000, status = Milestone.Status.ACTIVE)
        )

        val stats = service.getDreamStatistics(dream, milestones)

        assertEquals(3, stats.totalMilestones)
        assertEquals(1, stats.completedMilestones)
        // Total XP is sum of completed milestone target XP (M1 = 1000)
        assertEquals(1000L, stats.totalXpEarned)
    }

    // ========== Edge Cases ==========

    @Test
    fun `handles empty milestone list`() {
        val dream = createDream()
        val stats = service.getDreamStatistics(dream, emptyList())

        assertEquals(0, stats.totalMilestones)
        assertEquals(0, stats.completedMilestones)
    }

    @Test
    fun `handles milestone with 0 target XP`() {
        val milestone = createMilestone(1, "Test", currentXp = 100, targetXp = 0)
        // Should not crash, return safe value
        val progress = service.calculateMilestoneProgress(milestone)
        assertTrue(progress >= 0f)
    }
}
