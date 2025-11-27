package com.secretary.features.dreams.domain.service

/**
 * Implements self-regulation strategies from motivational psychology:
 * - Intent Control (Absichtskontrolle): Stay focused on goals
 * - Environment Control (Umweltkontrolle): Optimize surroundings
 * - Emotion Control (Emotionskontrolle): Handle setbacks
 *
 * Dream-to-Task Feature - Domain Layer
 * Clean Architecture: Pure Kotlin service with no Android dependencies
 */
class SelfRegulationService(
    private val progressService: DreamProgressService
) {

    companion object {
        private const val AHEAD_THRESHOLD = 10f
        private const val BEHIND_THRESHOLD = -10f
        private const val CRITICAL_THRESHOLD = -30f
    }

    /**
     * Get motivational message based on progress gap
     * Uses SOLL-IST analysis to provide context-appropriate feedback
     *
     * @param progressGap Gap percentage (-100 to +100)
     *                    Negative = behind schedule, Positive = ahead
     * @return Motivational message encouraging self-regulation
     */
    fun getMotivationalMessage(progressGap: Float): String {
        return when {
            progressGap >= AHEAD_THRESHOLD ->
                "Excellent progress! You're ahead of schedule. Keep the momentum going!"

            progressGap > 0 ->
                "You're on track and making steady progress. Great work!"

            progressGap >= BEHIND_THRESHOLD ->
                "Slightly behind schedule, but nothing you can't catch up on. Let's focus!"

            progressGap >= CRITICAL_THRESHOLD ->
                "Time to refocus your intentions. Small steps forward matter most."

            else ->
                "Significant gap detected. Let's break this down into smaller, achievable goals."
        }
    }

    /**
     * Get commitment reminder for intent control (Absichtskontrolle)
     * Helps maintain focus on original goals
     *
     * @param dreamTitle The dream being pursued
     * @param milestoneTitle Current milestone
     * @return Commitment prompt to reinforce intentions
     */
    fun getCommitmentPrompt(dreamTitle: String, milestoneTitle: String): String {
        return "Remember why you started: \"$dreamTitle\"\n" +
               "Your current focus: \"$milestoneTitle\"\n" +
               "Stay committed to your original intention."
    }

    /**
     * Get environment suggestion for task completion (Umweltkontrolle)
     * Provides actionable advice for optimizing surroundings
     *
     * @return Random environment optimization suggestion
     */
    fun getEnvironmentSuggestion(): String {
        val suggestions = listOf(
            "Create a distraction-free workspace for the next hour.",
            "Turn off notifications to maintain deep focus.",
            "Set up your environment before starting - tools ready, water nearby.",
            "Choose a quiet location where you can work uninterrupted.",
            "Remove temptations from your workspace to stay focused.",
            "Prepare everything you need in advance to avoid interruptions."
        )
        return suggestions.random()
    }

    /**
     * Get setback recovery message for emotion control (Emotionskontrolle)
     * Helps handle setbacks with psychological resilience
     *
     * @param daysSinceLastProgress Number of days since last milestone progress
     * @return Encouraging message to manage emotional response to setbacks
     */
    fun getSetbackMessage(daysSinceLastProgress: Int): String {
        return when {
            daysSinceLastProgress <= 2 ->
                "Don't worry about small pauses. Progress isn't always linear."

            daysSinceLastProgress <= 7 ->
                "It's been $daysSinceLastProgress days. Let's restart with a small, easy task."

            daysSinceLastProgress <= 14 ->
                "Setbacks are normal. The important thing is to begin again, even with tiny steps."

            else ->
                "Long pause detected. Start fresh - past setbacks don't define your future progress."
        }
    }

    /**
     * Analyze streak and provide feedback
     * Celebrates achievements and sets next milestones
     *
     * @param currentStreak Current consecutive days/completions
     * @return Detailed streak feedback with encouragement and next goal
     */
    fun analyzeStreak(currentStreak: Int): StreakFeedback {
        val (message, encouragement, nextMilestone) = when {
            currentStreak == 0 -> Triple(
                "New beginning! Start your streak today.",
                "Every journey begins with a single step.",
                1
            )

            currentStreak < 3 -> Triple(
                "Streak started: $currentStreak days!",
                "The first days are crucial. Keep going!",
                3
            )

            currentStreak < 7 -> Triple(
                "Building momentum: $currentStreak days!",
                "You're forming a habit. Don't break the chain!",
                7
            )

            currentStreak < 14 -> Triple(
                "One week streak: $currentStreak days!",
                "Impressive consistency! The habit is taking root.",
                14
            )

            currentStreak < 30 -> Triple(
                "Two week streak: $currentStreak days!",
                "Outstanding dedication! You're in the habit zone.",
                30
            )

            currentStreak < 60 -> Triple(
                "One month streak: $currentStreak days!",
                "Exceptional commitment! This is who you are now.",
                60
            )

            currentStreak < 100 -> Triple(
                "Epic streak: $currentStreak days!",
                "Legendary consistency! You're unstoppable.",
                100
            )

            else -> Triple(
                "Master streak: $currentStreak days!",
                "You've transcended streaks - this is your lifestyle.",
                null
            )
        }

        return StreakFeedback(
            message = message,
            encouragement = encouragement,
            nextMilestone = nextMilestone
        )
    }

    /**
     * Provide context-aware action suggestion
     * Combines multiple self-regulation strategies
     *
     * @param progressGap Current SOLL-IST gap
     * @param daysSinceProgress Days since last progress
     * @return Actionable suggestion combining multiple strategies
     */
    fun getActionSuggestion(progressGap: Float, daysSinceProgress: Int): String {
        return when {
            daysSinceProgress > 7 -> {
                // Emotion control - handle long pause
                "Focus on emotion control: ${getSetbackMessage(daysSinceProgress)}\n" +
                "Action: ${getEnvironmentSuggestion()}"
            }

            progressGap < CRITICAL_THRESHOLD -> {
                // Intent control - refocus on goals
                "Time to refocus your intentions. Review your dream and milestone.\n" +
                "Action: Start with just 10 minutes of focused work today."
            }

            progressGap < BEHIND_THRESHOLD -> {
                // Environment control - optimize conditions
                "Optimize your environment: ${getEnvironmentSuggestion()}\n" +
                "Small improvements in setup lead to big gains in productivity."
            }

            else -> {
                // Positive reinforcement
                "You're making good progress! ${getEnvironmentSuggestion()}"
            }
        }
    }
}

/**
 * Streak analysis feedback
 *
 * @property message Main streak status message
 * @property encouragement Motivational encouragement
 * @property nextMilestone Next streak milestone to aim for (null if already at max)
 */
data class StreakFeedback(
    val message: String,
    val encouragement: String,
    val nextMilestone: Int?
)
