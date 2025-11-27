package com.secretary.features.dreams.domain.service

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Unit tests for SelfRegulationService.
 * Tests self-regulation strategies: Intent Control, Environment Control, Emotion Control.
 */
class SelfRegulationServiceTest {

    private lateinit var service: SelfRegulationService
    private lateinit var mockProgressService: DreamProgressService

    @Before
    fun setup() {
        // Mock the DreamProgressService dependency (not used by methods under test)
        mockProgressService = mock(DreamProgressService::class.java)
        service = SelfRegulationService(mockProgressService)
    }

    // ========== getMotivationalMessage Tests ==========

    @Test
    fun `getMotivationalMessage gap above 10 returns ahead message`() {
        val message = service.getMotivationalMessage(15f)

        assertTrue(message.contains("Excellent progress"))
        assertTrue(message.contains("ahead of schedule"))
    }

    @Test
    fun `getMotivationalMessage gap between 0 and 10 returns on track message`() {
        val message = service.getMotivationalMessage(5f)

        assertTrue(message.contains("on track"))
        assertTrue(message.contains("steady progress"))
    }

    @Test
    fun `getMotivationalMessage gap between minus 10 and 0 returns slightly behind message`() {
        val message = service.getMotivationalMessage(-5f)

        assertTrue(message.contains("Slightly behind"))
        assertTrue(message.contains("catch up"))
    }

    @Test
    fun `getMotivationalMessage gap between minus 30 and minus 10 returns behind message`() {
        val message = service.getMotivationalMessage(-20f)

        assertTrue(message.contains("refocus"))
        assertTrue(message.contains("intentions"))
    }

    @Test
    fun `getMotivationalMessage gap below minus 30 returns critical message`() {
        val message = service.getMotivationalMessage(-40f)

        assertTrue(message.contains("Significant gap"))
        assertTrue(message.contains("smaller"))
        assertTrue(message.contains("achievable goals"))
    }

    // ========== getCommitmentPrompt Tests ==========

    @Test
    fun `getCommitmentPrompt formats correctly with titles`() {
        val dreamTitle = "Learn Guitar"
        val milestoneTitle = "Master Chord Transitions"

        val prompt = service.getCommitmentPrompt(dreamTitle, milestoneTitle)

        assertTrue(prompt.contains(dreamTitle))
        assertTrue(prompt.contains(milestoneTitle))
        assertTrue(prompt.contains("Remember why you started"))
        assertTrue(prompt.contains("current focus"))
        assertTrue(prompt.contains("committed"))
    }

    @Test
    fun `getCommitmentPrompt handles special characters in titles`() {
        val dreamTitle = "Learn C++ & Python!"
        val milestoneTitle = "Complete \"Advanced\" Course"

        val prompt = service.getCommitmentPrompt(dreamTitle, milestoneTitle)

        // Should contain the titles as-is without escaping issues
        assertTrue(prompt.contains("C++ & Python!"))
        assertTrue(prompt.contains("\"Advanced\""))
    }

    // ========== getSetbackMessage Tests ==========

    @Test
    fun `getSetbackMessage 0 to 2 days returns mild message`() {
        val message0 = service.getSetbackMessage(0)
        val message1 = service.getSetbackMessage(1)
        val message2 = service.getSetbackMessage(2)

        // All should be the same mild message
        assertTrue(message0.contains("small pauses"))
        assertTrue(message0.contains("isn't always linear"))
        assertEquals(message0, message1)
        assertEquals(message0, message2)
    }

    @Test
    fun `getSetbackMessage 3 to 7 days returns encouraging message`() {
        val message = service.getSetbackMessage(5)

        assertTrue(message.contains("5 days"))
        assertTrue(message.contains("restart"))
        assertTrue(message.contains("small"))
        assertTrue(message.contains("easy task"))
    }

    @Test
    fun `getSetbackMessage 8 to 14 days returns recovery message`() {
        val message = service.getSetbackMessage(10)

        assertTrue(message.contains("Setbacks are normal"))
        assertTrue(message.contains("begin again"))
        assertTrue(message.contains("tiny steps"))
    }

    @Test
    fun `getSetbackMessage over 14 days returns strong support message`() {
        val message = service.getSetbackMessage(30)

        assertTrue(message.contains("Long pause"))
        assertTrue(message.contains("Start fresh"))
        assertTrue(message.contains("don't define"))
    }

    // ========== analyzeStreak Tests ==========

    @Test
    fun `analyzeStreak zero streak returns starting feedback`() {
        val feedback = service.analyzeStreak(0)

        assertEquals("New beginning! Start your streak today.", feedback.message)
        assertTrue(feedback.encouragement.contains("single step"))
        assertEquals(1, feedback.nextMilestone)
    }

    @Test
    fun `analyzeStreak moderate streak returns progress feedback`() {
        // Test streak of 5 days (in the 3-7 range)
        val feedback = service.analyzeStreak(5)

        assertTrue(feedback.message.contains("Building momentum"))
        assertTrue(feedback.message.contains("5 days"))
        assertTrue(feedback.encouragement.contains("habit"))
        assertEquals(7, feedback.nextMilestone)
    }

    @Test
    fun `analyzeStreak master streak returns master feedback`() {
        // Test streak >= 100
        val feedback = service.analyzeStreak(150)

        assertTrue(feedback.message.contains("Master streak"))
        assertTrue(feedback.message.contains("150 days"))
        assertTrue(feedback.encouragement.contains("lifestyle"))
        assertNull(feedback.nextMilestone) // No next milestone for master level
    }

    // ========== getActionSuggestion Tests ==========

    @Test
    fun `getActionSuggestion combines strategies based on gap and days`() {
        // Test case 1: Long pause (> 7 days) - should prioritize emotion control
        val suggestion1 = service.getActionSuggestion(-5f, 10)
        assertTrue(suggestion1.contains("emotion control"))
        assertTrue(suggestion1.contains("Action:"))

        // Test case 2: Critical gap (< -30) - should prioritize intent control
        val suggestion2 = service.getActionSuggestion(-35f, 3)
        assertTrue(suggestion2.contains("refocus your intentions"))
        assertTrue(suggestion2.contains("10 minutes"))

        // Test case 3: Behind schedule (< -10 but >= -30) - should prioritize environment control
        val suggestion3 = service.getActionSuggestion(-15f, 2)
        assertTrue(suggestion3.contains("Optimize your environment"))
        assertTrue(suggestion3.contains("productivity"))

        // Test case 4: On track or ahead - should give positive reinforcement
        val suggestion4 = service.getActionSuggestion(5f, 1)
        assertTrue(suggestion4.contains("good progress"))
    }

    // ========== Edge Cases and Boundary Tests ==========

    @Test
    fun `getMotivationalMessage handles exact threshold values`() {
        // Test exact boundaries
        val messageAt10 = service.getMotivationalMessage(10f)
        assertTrue(messageAt10.contains("Excellent progress"))

        // At 0, the condition progressGap >= BEHIND_THRESHOLD (-10) is true
        // So it falls into the "Slightly behind" category
        val messageAt0 = service.getMotivationalMessage(0f)
        assertTrue(messageAt0.contains("Slightly behind"))

        val messageAtMinus10 = service.getMotivationalMessage(-10f)
        assertTrue(messageAtMinus10.contains("Slightly behind"))

        val messageAtMinus30 = service.getMotivationalMessage(-30f)
        assertTrue(messageAtMinus30.contains("refocus"))
    }

    @Test
    fun `analyzeStreak handles all milestone boundaries`() {
        // Test key boundaries in streak progression
        val feedback1 = service.analyzeStreak(1)
        assertEquals(3, feedback1.nextMilestone)

        val feedback3 = service.analyzeStreak(3)
        assertEquals(7, feedback3.nextMilestone)

        val feedback7 = service.analyzeStreak(7)
        assertEquals(14, feedback7.nextMilestone)

        val feedback14 = service.analyzeStreak(14)
        assertEquals(30, feedback14.nextMilestone)

        val feedback30 = service.analyzeStreak(30)
        assertEquals(60, feedback30.nextMilestone)

        val feedback60 = service.analyzeStreak(60)
        assertEquals(100, feedback60.nextMilestone)

        val feedback100 = service.analyzeStreak(100)
        assertNull(feedback100.nextMilestone)
    }

    @Test
    fun `getSetbackMessage handles boundary days`() {
        // Test exact boundaries
        val message2 = service.getSetbackMessage(2)
        assertTrue(message2.contains("small pauses"))

        val message3 = service.getSetbackMessage(3)
        assertTrue(message3.contains("3 days"))

        val message7 = service.getSetbackMessage(7)
        assertTrue(message7.contains("7 days"))

        val message8 = service.getSetbackMessage(8)
        assertTrue(message8.contains("Setbacks are normal"))

        val message14 = service.getSetbackMessage(14)
        assertTrue(message14.contains("Setbacks are normal"))

        val message15 = service.getSetbackMessage(15)
        assertTrue(message15.contains("Long pause"))
    }

    @Test
    fun `getCommitmentPrompt works with empty strings`() {
        // Should handle edge case of empty titles gracefully
        val prompt = service.getCommitmentPrompt("", "")

        assertNotNull(prompt)
        assertTrue(prompt.contains("Remember why you started"))
    }

    @Test
    fun `analyzeStreak returns valid feedback for all ranges`() {
        // Test that all streak ranges return proper StreakFeedback structure
        val testStreaks = listOf(0, 1, 5, 10, 20, 50, 120)

        for (streak in testStreaks) {
            val feedback = service.analyzeStreak(streak)

            assertNotNull(feedback.message)
            assertNotNull(feedback.encouragement)
            assertFalse(feedback.message.isEmpty())
            assertFalse(feedback.encouragement.isEmpty())

            // nextMilestone should be null only for master streaks (>= 100)
            if (streak >= 100) {
                assertNull(feedback.nextMilestone)
            } else {
                assertNotNull(feedback.nextMilestone)
                assertTrue(feedback.nextMilestone!! > streak)
            }
        }
    }
}
