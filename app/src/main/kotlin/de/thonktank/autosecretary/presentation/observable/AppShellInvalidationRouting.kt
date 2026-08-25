package de.thonktank.autosecretary.presentation.observable

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

/** Routes only appearance work into the temporary legacy shell owner. */
class AppShellInvalidationRouting internal constructor(
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
}
