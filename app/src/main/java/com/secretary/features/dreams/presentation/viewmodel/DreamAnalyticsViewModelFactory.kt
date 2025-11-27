package com.secretary.features.dreams.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.secretary.features.dreams.data.dao.DreamAnalyticsDao
import com.secretary.features.dreams.data.dao.DreamDao
import com.secretary.features.dreams.data.dao.MilestoneDao

/**
 * Factory for creating DreamAnalyticsViewModel instances.
 * Dream Analytics Feature - Presentation Layer
 *
 * Creates ViewModels with the required dependencies:
 * - dreamId: ID of the dream to analyze
 * - DAOs for data access
 *
 * Part of Phase 5.2: Dream Analytics
 * Related: DreamAnalyticsViewModel, DreamAnalyticsFragment
 */
class DreamAnalyticsViewModelFactory(
    private val dreamId: Long,
    private val dreamDao: DreamDao,
    private val milestoneDao: MilestoneDao,
    private val analyticsDao: DreamAnalyticsDao
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DreamAnalyticsViewModel::class.java)) {
            return DreamAnalyticsViewModel(
                dreamId = dreamId,
                dreamDao = dreamDao,
                milestoneDao = milestoneDao,
                analyticsDao = analyticsDao
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
