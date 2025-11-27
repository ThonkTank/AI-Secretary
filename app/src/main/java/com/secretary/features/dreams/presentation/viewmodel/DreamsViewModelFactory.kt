package com.secretary.features.dreams.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.secretary.features.dreams.data.dao.DreamDao
import com.secretary.features.dreams.data.dao.MilestoneDao

/**
 * Factory for creating DreamsViewModel with dependencies.
 */
class DreamsViewModelFactory(
    private val dreamDao: DreamDao,
    private val milestoneDao: MilestoneDao
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DreamsViewModel::class.java)) {
            return DreamsViewModel(dreamDao, milestoneDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
