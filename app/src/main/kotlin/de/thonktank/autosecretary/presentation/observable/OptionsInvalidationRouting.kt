package de.thonktank.autosecretary.presentation.observable

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

/** Routes only options-owned projections out of the shared dashboard invalidation stream. */
class OptionsInvalidationRouting internal constructor(
    dashboardChanges: Flow<PresentationInvalidation>,
) {
    constructor(invalidations: PresentationInvalidationSource) : this(
        invalidations.dashboardChanges,
    )

    val appearanceChanges: Flow<PresentationInvalidation> =
        dashboardChanges.filter { event ->
            event.cause == PresentationInvalidationCause.INITIAL ||
                event.cause == PresentationInvalidationCause.DISPLAY_PREFERENCES ||
                event.cause == PresentationInvalidationCause.CLOCK
        }

    val calendarChanges: Flow<PresentationInvalidation> =
        dashboardChanges.filter { event ->
            event.cause == PresentationInvalidationCause.INITIAL ||
                event.cause == PresentationInvalidationCause.CALENDAR ||
                event.cause == PresentationInvalidationCause.CALENDAR_POLICY
        }
}
