package de.thonktank.autosecretary.presentation.observable

import de.thonktank.autosecretary.data.observable.ClockInvalidationReason
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

fun interface LoadedDashboardDate {
    fun get(): LocalDate?
}

/** Separates projection work from cheap appearance updates without losing day-boundary reads. */
class DashboardInvalidationRouting internal constructor(
    dashboardChanges: Flow<PresentationInvalidation>,
    loadedDate: LoadedDashboardDate,
) {
    constructor(
        invalidations: PresentationInvalidationSource,
        loadedDate: LoadedDashboardDate,
    ) : this(invalidations.dashboardChanges, loadedDate)

    val contentChanges: Flow<PresentationInvalidation> = dashboardChanges.filter { event ->
        requiresContentRead(event, loadedDate.get())
    }

    val appearanceChanges: Flow<PresentationInvalidation> = dashboardChanges.filter { event ->
        event.cause == PresentationInvalidationCause.DISPLAY_PREFERENCES ||
            event.cause == PresentationInvalidationCause.CLOCK
    }

    internal companion object {
        fun requiresContentRead(
            event: PresentationInvalidation,
            loadedDate: LocalDate?,
        ): Boolean = when (event.cause) {
            PresentationInvalidationCause.INITIAL,
            PresentationInvalidationCause.DATABASE,
            PresentationInvalidationCause.CALENDAR,
            PresentationInvalidationCause.CALENDAR_POLICY,
            -> true
            PresentationInvalidationCause.DISPLAY_PREFERENCES -> false
            PresentationInvalidationCause.WIDGET_HOST -> false
            PresentationInvalidationCause.CLOCK -> {
                val snapshot = event.clock ?: return true
                snapshot.reason != ClockInvalidationReason.MINUTE_TICK ||
                    loadedDate == null || snapshot.date != loadedDate
            }
        }
    }
}
