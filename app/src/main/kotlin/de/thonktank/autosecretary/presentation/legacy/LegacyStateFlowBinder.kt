package de.thonktank.autosecretary.presentation.legacy

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.StateFlow

/** Temporary lifecycle bridge for legacy Views; screen owners still expose only StateFlow. */
object LegacyStateFlowBinder {
    @JvmStatic
    fun <T> observe(owner: LifecycleOwner, state: StateFlow<T>, observer: Observer<T>) {
        state.asLiveData().observe(owner, observer)
    }
}
