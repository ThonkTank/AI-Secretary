package com.secretary.features.dreams.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretary.features.dreams.data.dao.DreamDao
import com.secretary.features.dreams.data.dao.MilestoneDao
import com.secretary.features.dreams.domain.model.Dream
import kotlinx.coroutines.launch

/**
 * ViewModel for DreamsFragment.
 * Manages dream list state and loading.
 */
class DreamsViewModel(
    private val dreamDao: DreamDao,
    private val milestoneDao: MilestoneDao
) : ViewModel() {

    private val _dreams = MutableLiveData<List<Dream>>()
    val dreams: LiveData<List<Dream>> = _dreams

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        loadDreams()
    }

    fun loadDreams() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val dreamEntities = dreamDao.getActiveDreams()
                val dreamList = dreamEntities.map { entity ->
                    Dream(
                        id = entity.dreamId,
                        title = entity.title,
                        description = entity.description,
                        iconName = entity.iconName,
                        color = entity.color,
                        totalXp = entity.totalXp,
                        currentLevel = entity.currentLevel,
                        createdAt = entity.createdAt,
                        isArchived = entity.isArchived
                    )
                }
                _dreams.value = dreamList
            } catch (e: Exception) {
                _dreams.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
