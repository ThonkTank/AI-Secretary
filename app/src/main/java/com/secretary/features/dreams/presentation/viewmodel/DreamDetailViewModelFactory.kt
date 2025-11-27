package com.secretary.features.dreams.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.secretary.features.dreams.data.dao.DreamDao
import com.secretary.features.dreams.data.dao.MilestoneDao

/**
 * Factory for creating DreamDetailViewModel instances.
 * Dream-to-Task Feature - Presentation Layer
 *
 * Required by ViewModelProvider to instantiate ViewModels with constructor parameters.
 * Provides the necessary dependencies (dreamId, DAOs) to the ViewModel.
 *
 * Part of Wave 4: Dream Detail View
 * Related: DreamDetailViewModel, DreamDetailFragment
 */
class DreamDetailViewModelFactory(
    private val dreamId: Long,
    private val dreamDao: DreamDao,
    private val milestoneDao: MilestoneDao
) : ViewModelProvider.Factory {

    /**
     * Create a new instance of the given ViewModel class.
     *
     * @param modelClass The ViewModel class to instantiate
     * @return A newly created ViewModel instance
     * @throws IllegalArgumentException if the ViewModel class is not DreamDetailViewModel
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DreamDetailViewModel::class.java)) {
            return DreamDetailViewModel(dreamId, dreamDao, milestoneDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
