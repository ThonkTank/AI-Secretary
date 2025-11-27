package com.secretary.features.dreams.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretary.features.dreams.data.dao.DreamDao
import com.secretary.features.dreams.data.dao.MilestoneDao
import com.secretary.features.dreams.domain.model.Dream
import com.secretary.features.dreams.domain.model.Milestone
import com.secretary.features.dreams.domain.service.DreamProgressService
import com.secretary.features.dreams.domain.service.SelfRegulationService
import com.secretary.features.dreams.presentation.components.GapLevel
import com.secretary.features.dreams.presentation.components.SelfRegulationState
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * ViewModel for DreamDetailFragment.
 * Dream-to-Task Feature - Presentation Layer
 *
 * Manages the state and data loading for a single dream detail view.
 * Coordinates between the UI layer and data layer using LiveData for reactive updates.
 *
 * Responsibilities:
 * - Load dream and its milestones from database
 * - Expose data as LiveData for UI observation
 * - Handle loading states
 *
 * Part of Wave 4: Dream Detail View
 * Related: DreamDetailFragment, DreamDao, MilestoneDao
 */
class DreamDetailViewModel(
    private val dreamId: Long,
    private val dreamDao: DreamDao,
    private val milestoneDao: MilestoneDao,
    private val progressService: DreamProgressService = DreamProgressService(),
    private val selfRegulationService: SelfRegulationService = SelfRegulationService(DreamProgressService())
) : ViewModel() {

    private val _dream = MutableLiveData<Dream?>()
    val dream: LiveData<Dream?> = _dream

    private val _milestones = MutableLiveData<List<Milestone>>()
    val milestones: LiveData<List<Milestone>> = _milestones

    private val _selfRegulationState = MutableLiveData<SelfRegulationState?>()
    val selfRegulationState: LiveData<SelfRegulationState?> = _selfRegulationState

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        loadData()
    }

    /**
     * Load dream and milestones from database.
     *
     * Fetches data asynchronously using coroutines and updates LiveData.
     * Handles errors gracefully by setting null/empty values.
     */
    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load dream
                val dreamEntity = dreamDao.getDreamById(dreamId)
                _dream.value = dreamEntity?.let {
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

                // Load milestones
                val milestoneEntities = milestoneDao.getMilestonesByDream(dreamId)
                val milestones = milestoneEntities.map { entity ->
                    Milestone(
                        id = entity.milestoneId,
                        dreamId = entity.dreamId,
                        title = entity.title,
                        description = entity.description,
                        targetXp = entity.targetXp,
                        currentXp = entity.currentXp,
                        status = Milestone.Status.fromInt(entity.status),
                        createdAt = entity.createdAt,
                        completedAt = entity.completedAt,
                        orderIndex = entity.orderIndex
                    )
                }
                _milestones.value = milestones

                // Calculate self-regulation state
                _selfRegulationState.value = calculateSelfRegulationState(_dream.value, milestones)
            } catch (e: Exception) {
                // On error, set default values
                _dream.value = null
                _milestones.value = emptyList()
                _selfRegulationState.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Calculate self-regulation state based on dream and milestone data.
     * Uses SOLL-IST gap analysis and motivational messaging.
     *
     * @param dream The dream to analyze (nullable)
     * @param milestones List of milestones for the dream
     * @return SelfRegulationState or null if insufficient data
     */
    private fun calculateSelfRegulationState(
        dream: Dream?,
        milestones: List<Milestone>
    ): SelfRegulationState? {
        if (dream == null || milestones.isEmpty()) return null

        // Calculate actual progress (IST)
        val actualProgress = progressService.calculateDreamProgress(milestones)

        // Calculate expected progress (SOLL)
        // Assume 90-day timeline for simplicity
        val daysElapsed = TimeUnit.MILLISECONDS.toDays(
            System.currentTimeMillis() - dream.createdAt
        ).toFloat()
        val expectedDays = 90f
        val expectedProgress = (daysElapsed / expectedDays).coerceIn(0f, 1f)

        // Calculate gap percentage
        val gap = progressService.calculateProgressGap(expectedProgress, actualProgress)

        // Determine gap level
        val gapLevel = when {
            gap >= 10f -> GapLevel.AHEAD
            gap >= 0f -> GapLevel.ON_TRACK
            gap >= -10f -> GapLevel.SLIGHTLY_BEHIND
            gap >= -30f -> GapLevel.BEHIND
            else -> GapLevel.CRITICAL
        }

        // Get motivational message
        val message = selfRegulationService.getMotivationalMessage(gap)

        return SelfRegulationState(
            gapPercentage = gap,
            gapLevel = gapLevel,
            motivationalMessage = message
        )
    }
}
