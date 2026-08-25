package de.thonktank.autosecretary.presentation.observable

import de.thonktank.autosecretary.calendar.CalendarPolicy
import de.thonktank.autosecretary.data.local.RoomInvalidationSource
import de.thonktank.autosecretary.data.observable.CalendarInvalidationSource
import de.thonktank.autosecretary.data.observable.ClockInvalidationSource
import de.thonktank.autosecretary.data.observable.ClockSnapshot
import de.thonktank.autosecretary.data.observable.PreferenceInvalidationSource
import de.thonktank.autosecretary.data.preferences.DisplayPreferences
import java.io.Closeable
import java.util.concurrent.Executor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn

enum class PresentationInvalidationTarget {
    DASHBOARD,
    CATALOG,
    WIDGETS,
}

enum class PresentationInvalidationCause {
    INITIAL,
    DATABASE,
    CALENDAR,
    DISPLAY_PREFERENCES,
    CALENDAR_POLICY,
    CLOCK,
    WIDGET_HOST,
}

/** One typed reason to recompute one or more presentation projections. */
data class PresentationInvalidation(
    val cause: PresentationInvalidationCause,
    val targets: Set<PresentationInvalidationTarget>,
    val changedTables: Set<String> = emptySet(),
    val displayPreferences: DisplayPreferences? = null,
    val calendarPolicy: CalendarPolicy? = null,
    val clock: ClockSnapshot? = null,
) {
    companion object {
        internal fun initial(target: PresentationInvalidationTarget) = PresentationInvalidation(
            cause = PresentationInvalidationCause.INITIAL,
            targets = setOf(target),
        )
    }
}

/**
 * Process-local presentation invalidations shared by every active consumer.
 *
 * The underlying cold sources are subscribed at most once and only while at least one target
 * flow is collected. Every target flow owns its initial impulse, independent of shared history.
 */
class PresentationInvalidationSource internal constructor(
    databaseChanges: Flow<Set<String>>,
    calendarChanges: Flow<Unit>,
    displayPreferenceChanges: Flow<DisplayPreferences>,
    calendarPolicyChanges: Flow<CalendarPolicy>,
    clockChanges: Flow<ClockSnapshot>,
    sharingDispatcher: CoroutineDispatcher,
) : Closeable {
    constructor(
        databaseInvalidations: RoomInvalidationSource,
        calendarInvalidations: CalendarInvalidationSource,
        preferenceInvalidations: PreferenceInvalidationSource,
        clockInvalidations: ClockInvalidationSource,
    ) : this(
        databaseChanges = databaseInvalidations.changes,
        calendarChanges = calendarInvalidations.changes,
        displayPreferenceChanges = preferenceInvalidations.displayPreferences,
        calendarPolicyChanges = preferenceInvalidations.calendarPolicy,
        clockChanges = clockInvalidations.changes,
        sharingDispatcher = Dispatchers.Default,
    )

    /** Deterministic Java-friendly construction for tests without changing source ownership. */
    constructor(
        databaseInvalidations: RoomInvalidationSource,
        calendarInvalidations: CalendarInvalidationSource,
        preferenceInvalidations: PreferenceInvalidationSource,
        clockInvalidations: ClockInvalidationSource,
        sharingExecutor: Executor,
    ) : this(
        databaseChanges = databaseInvalidations.changes,
        calendarChanges = calendarInvalidations.changes,
        displayPreferenceChanges = preferenceInvalidations.displayPreferences,
        calendarPolicyChanges = preferenceInvalidations.calendarPolicy,
        clockChanges = clockInvalidations.changes,
        sharingDispatcher = sharingExecutor.asCoroutineDispatcher(),
    )

    private val scope = CoroutineScope(SupervisorJob() + sharingDispatcher)
    private val closed = CompletableDeferred<Unit>()
    private val widgetHostChanges = MutableSharedFlow<PresentationInvalidation>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val shared = merge(
        databaseChanges.map { tables ->
            PresentationInvalidation(
                cause = PresentationInvalidationCause.DATABASE,
                targets = ALL_TARGETS,
                changedTables = tables,
            )
        },
        calendarChanges.map {
            PresentationInvalidation(
                cause = PresentationInvalidationCause.CALENDAR,
                targets = DASHBOARD_AND_WIDGETS,
            )
        },
        displayPreferenceChanges.map { preferences ->
            PresentationInvalidation(
                cause = PresentationInvalidationCause.DISPLAY_PREFERENCES,
                targets = DASHBOARD_AND_WIDGETS,
                displayPreferences = preferences,
            )
        },
        calendarPolicyChanges.map { policy ->
            PresentationInvalidation(
                cause = PresentationInvalidationCause.CALENDAR_POLICY,
                targets = DASHBOARD_AND_WIDGETS,
                calendarPolicy = policy,
            )
        },
        clockChanges.map { snapshot ->
            PresentationInvalidation(
                cause = PresentationInvalidationCause.CLOCK,
                targets = DASHBOARD_AND_WIDGETS,
                clock = snapshot,
            )
        },
        widgetHostChanges,
    ).shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(
            stopTimeoutMillis = 0,
            replayExpirationMillis = 0,
        ),
        replay = 0,
    )

    val dashboardChanges: Flow<PresentationInvalidation> = forTarget(
        PresentationInvalidationTarget.DASHBOARD,
    )
    val catalogChanges: Flow<PresentationInvalidation> = forTarget(
        PresentationInvalidationTarget.CATALOG,
    )
    val widgetChanges: Flow<PresentationInvalidation> = forTarget(
        PresentationInvalidationTarget.WIDGETS,
    )

    /** Requests a fresh widget projection for provider lifecycle or size changes. */
    fun materializeWidgetHostChange() {
        widgetHostChanges.tryEmit(
            PresentationInvalidation(
                cause = PresentationInvalidationCause.WIDGET_HOST,
                targets = setOf(PresentationInvalidationTarget.WIDGETS),
            ),
        )
    }

    private fun forTarget(
        target: PresentationInvalidationTarget,
    ): Flow<PresentationInvalidation> = channelFlow {
        val initialDelivered = CompletableDeferred<Unit>()
        val sharedCollection = launch(start = CoroutineStart.UNDISPATCHED) {
            shared.filter { target in it.targets }.collect { event ->
                initialDelivered.await()
                send(event)
            }
        }
        send(PresentationInvalidation.initial(target))
        initialDelivered.complete(Unit)
        closed.await()
        sharedCollection.cancelAndJoin()
    }

    override fun close() {
        closed.complete(Unit)
        scope.cancel()
    }

    private companion object {
        val ALL_TARGETS = PresentationInvalidationTarget.entries.toSet()
        val DASHBOARD_AND_WIDGETS = setOf(
            PresentationInvalidationTarget.DASHBOARD,
            PresentationInvalidationTarget.WIDGETS,
        )
    }
}
