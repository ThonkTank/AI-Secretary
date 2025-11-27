package com.secretary.features.dreams.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.secretary.features.dreams.data.dao.DreamAnalyticsDao
import com.secretary.features.dreams.data.dao.DreamDao
import com.secretary.features.dreams.data.dao.MilestoneDao
import com.secretary.features.dreams.domain.model.Dream
import com.secretary.features.dreams.domain.service.DreamProgressService
import com.secretary.features.dreams.domain.service.SelfRegulationService
import com.secretary.features.dreams.presentation.components.SelfRegulationState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * ViewModel for Dream Analytics Dashboard.
 * Dream Analytics Feature - Presentation Layer
 *
 * Manages the state and data for the analytics view:
 * - XP history chart data
 * - Time range filtering (7d, 30d, all)
 * - Streak insights
 * - Strategy recommendations
 *
 * Part of Phase 5.2: Dream Analytics
 * Related: DreamAnalyticsFragment, DreamAnalyticsDao
 */
class DreamAnalyticsViewModel(
    private val dreamId: Long,
    private val dreamDao: DreamDao,
    private val milestoneDao: MilestoneDao,
    private val analyticsDao: DreamAnalyticsDao,
    private val progressService: DreamProgressService = DreamProgressService(),
    private val selfRegulationService: SelfRegulationService = SelfRegulationService(DreamProgressService())
) : ViewModel() {

    companion object {
        private const val DAYS_7 = 7
        private const val DAYS_30 = 30
    }

    enum class TimeRange {
        DAYS_7, DAYS_30, ALL
    }

    private val _dream = MutableLiveData<Dream?>()
    val dream: LiveData<Dream?> = _dream

    private val _chartData = MutableLiveData<LineData?>()
    val chartData: LiveData<LineData?> = _chartData

    private val _hasData = MutableLiveData<Boolean>()
    val hasData: LiveData<Boolean> = _hasData

    private val _milestonesCompleted = MutableLiveData<Int>()
    val milestonesCompleted: LiveData<Int> = _milestonesCompleted

    private val _streakDays = MutableLiveData<Int>()
    val streakDays: LiveData<Int> = _streakDays

    private val _streakInsight = MutableLiveData<String>()
    val streakInsight: LiveData<String> = _streakInsight

    private val _strategyRecommendations = MutableLiveData<String>()
    val strategyRecommendations: LiveData<String> = _strategyRecommendations

    private val _selfRegulationState = MutableLiveData<SelfRegulationState?>()
    val selfRegulationState: LiveData<SelfRegulationState?> = _selfRegulationState

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private var currentTimeRange = TimeRange.DAYS_7

    init {
        loadData()
    }

    /**
     * Load all analytics data from database.
     */
    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load dream
                val dreamEntity = dreamDao.getDreamById(dreamId)
                val dream = dreamEntity?.let {
                    Dream(
                        id = it.dreamId,
                        title = it.title,
                        description = it.description,
                        iconName = it.iconName,
                        color = it.color,
                        totalXp = it.totalXp,
                        currentLevel = it.currentLevel,
                        createdAt = it.createdAt,
                        isArchived = it.isArchived
                    )
                }
                _dream.value = dream

                // Load milestones
                val milestoneEntities = milestoneDao.getMilestonesByDream(dreamId)
                val completedCount = milestoneEntities.count { it.status == 1 }
                _milestonesCompleted.value = completedCount

                // Calculate streak (simplified - using days since creation as proxy)
                val daysSinceCreation = if (dream != null) {
                    TimeUnit.MILLISECONDS.toDays(
                        System.currentTimeMillis() - dream.createdAt
                    ).toInt()
                } else {
                    0
                }
                _streakDays.value = daysSinceCreation.coerceAtLeast(0)

                // Load XP history and update chart
                updateChartData(currentTimeRange)

                // Calculate self-regulation state
                _selfRegulationState.value = calculateSelfRegulationState(dream, milestoneEntities.map { entity ->
                    com.secretary.features.dreams.domain.model.Milestone(
                        id = entity.milestoneId,
                        dreamId = entity.dreamId,
                        title = entity.title,
                        description = entity.description,
                        targetXp = entity.targetXp,
                        currentXp = entity.currentXp,
                        status = com.secretary.features.dreams.domain.model.Milestone.Status.fromInt(entity.status),
                        createdAt = entity.createdAt,
                        completedAt = entity.completedAt,
                        orderIndex = entity.orderIndex
                    )
                })

                // Calculate streak insight
                _streakInsight.value = calculateStreakInsight(_streakDays.value ?: 0)

                // Generate strategy recommendations
                _strategyRecommendations.value = generateStrategyRecommendations(
                    dream,
                    milestoneEntities.size,
                    completedCount
                )
            } catch (e: Exception) {
                _dream.value = null
                _hasData.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Update time range filter and reload chart data.
     */
    fun setTimeRange(timeRange: TimeRange) {
        currentTimeRange = timeRange
        viewModelScope.launch {
            updateChartData(timeRange)
        }
    }

    /**
     * Update chart data based on selected time range.
     */
    private suspend fun updateChartData(timeRange: TimeRange) {
        try {
            val now = System.currentTimeMillis()
            val sinceTimestamp = when (timeRange) {
                TimeRange.DAYS_7 -> now - TimeUnit.DAYS.toMillis(DAYS_7.toLong())
                TimeRange.DAYS_30 -> now - TimeUnit.DAYS.toMillis(DAYS_30.toLong())
                TimeRange.ALL -> 0L
            }

            // Get snapshots for the time range
            val snapshots = if (timeRange == TimeRange.ALL) {
                analyticsDao.getSnapshotsForDream(dreamId)
            } else {
                analyticsDao.getSnapshotsSince(dreamId, sinceTimestamp)
            }.reversed() // Order by oldest first for chart

            if (snapshots.isEmpty()) {
                _hasData.value = false
                _chartData.value = null
                return
            }

            // Convert snapshots to chart entries
            val entries = snapshots.mapIndexed { index, snapshot ->
                Entry(index.toFloat(), snapshot.totalXp.toFloat())
            }

            // Create dataset
            val dataSet = LineDataSet(entries, "XP").apply {
                color = 0xFF1E88E5.toInt() // Primary blue
                setCircleColor(0xFF1E88E5.toInt())
                lineWidth = 2f
                circleRadius = 3f
                setDrawCircleHole(false)
                setDrawValues(false)
                setDrawFilled(true)
                fillColor = 0x801E88E5.toInt() // Semi-transparent blue
                mode = LineDataSet.Mode.CUBIC_BEZIER // Smooth curve
            }

            _chartData.value = LineData(dataSet)
            _hasData.value = true
        } catch (e: Exception) {
            _hasData.value = false
            _chartData.value = null
        }
    }

    /**
     * Calculate self-regulation state (SOLL-IST analysis).
     */
    private fun calculateSelfRegulationState(
        dream: Dream?,
        milestones: List<com.secretary.features.dreams.domain.model.Milestone>
    ): SelfRegulationState? {
        if (dream == null || milestones.isEmpty()) return null

        val actualProgress = progressService.calculateDreamProgress(milestones)
        val daysElapsed = TimeUnit.MILLISECONDS.toDays(
            System.currentTimeMillis() - dream.createdAt
        ).toFloat()
        val expectedDays = 90f
        val expectedProgress = (daysElapsed / expectedDays).coerceIn(0f, 1f)
        val gap = progressService.calculateProgressGap(expectedProgress, actualProgress)

        val gapLevel = when {
            gap >= 10f -> com.secretary.features.dreams.presentation.components.GapLevel.AHEAD
            gap >= 0f -> com.secretary.features.dreams.presentation.components.GapLevel.ON_TRACK
            gap >= -10f -> com.secretary.features.dreams.presentation.components.GapLevel.SLIGHTLY_BEHIND
            gap >= -30f -> com.secretary.features.dreams.presentation.components.GapLevel.BEHIND
            else -> com.secretary.features.dreams.presentation.components.GapLevel.CRITICAL
        }

        return SelfRegulationState(
            gapPercentage = gap,
            gapLevel = gapLevel,
            motivationalMessage = selfRegulationService.getMotivationalMessage(gap)
        )
    }

    /**
     * Calculate streak insight message.
     */
    private fun calculateStreakInsight(streakDays: Int): String {
        return when {
            streakDays >= 30 -> "Hervorragend! $streakDays Tage aktiv!"
            streakDays >= 7 -> "Gut dabei - $streakDays Tage in Serie!"
            streakDays > 0 -> "Starte deine Serie heute!"
            else -> "Beginne jetzt deine Reise!"
        }
    }

    /**
     * Generate strategy recommendations based on dream progress.
     */
    private fun generateStrategyRecommendations(
        dream: Dream?,
        totalMilestones: Int,
        completedMilestones: Int
    ): String {
        if (dream == null) return ""

        val recommendations = mutableListOf<String>()

        if (totalMilestones == 0) {
            recommendations.add("• Erstelle Meilensteine, um dein Ziel zu strukturieren")
        } else {
            val progress = if (totalMilestones > 0) {
                completedMilestones.toFloat() / totalMilestones
            } else 0f

            when {
                progress == 0f -> {
                    recommendations.add("• Beginne mit dem ersten Meilenstein")
                    recommendations.add("• Verknüpfe tägliche Tasks mit deinen Zielen")
                }
                progress < 0.3f -> {
                    recommendations.add("• Fokussiere dich auf die nächsten 2-3 Meilensteine")
                    recommendations.add("• Erstelle kleinere Zwischen-Meilensteine für schnellere Erfolge")
                }
                progress < 0.7f -> {
                    recommendations.add("• Du bist auf einem guten Weg - bleib dran!")
                    recommendations.add("• Überlege, welche Meilensteine voneinander abhängen")
                }
                progress < 1.0f -> {
                    recommendations.add("• Fast geschafft! Konzentriere dich auf die letzten Schritte")
                    recommendations.add("• Plane bereits die nächsten Ziele")
                }
                else -> {
                    recommendations.add("• Glückwunsch! Du hast alle Meilensteine abgeschlossen")
                    recommendations.add("• Zeit für neue Herausforderungen")
                }
            }

            if (dream.currentLevel < 5) {
                recommendations.add("• Sammle mehr XP, um das nächste Level zu erreichen")
            }
        }

        return recommendations.joinToString("\n")
    }
}
