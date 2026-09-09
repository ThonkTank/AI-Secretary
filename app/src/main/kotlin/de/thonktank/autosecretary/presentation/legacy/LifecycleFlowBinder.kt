package de.thonktank.autosecretary.presentation.legacy

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Lifecycle bridge for a legacy Activity that consumes a non-state presentation signal. */
object LifecycleFlowBinder {
    fun interface Observer<T> {
        fun onChanged(value: T)
    }

    @JvmStatic
    fun <T> observe(owner: LifecycleOwner, flow: Flow<T>, observer: Observer<T>) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flow.collect(observer::onChanged)
            }
        }
    }
}
