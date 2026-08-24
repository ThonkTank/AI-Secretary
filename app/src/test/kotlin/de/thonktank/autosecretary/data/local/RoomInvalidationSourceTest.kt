package de.thonktank.autosecretary.data.local

import android.content.Context
import androidx.room.InvalidationTracker
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.thonktank.autosecretary.AppDatabase
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomInvalidationSourceTest {
    private val schema16Tables = setOf(
        "tasks",
        "task_steps",
        "task_schedule_entries",
        "occurrences",
        "occurrence_steps",
        "repetition_results",
        "stats",
        "combo_progress",
        "reward_bookings",
        "reward_assignments",
    )
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun initialEmissionContainsTheCompleteSchema16TableContract() = runBlocking {
        val emissions = RoomInvalidationSource(database).changes.produceIn(this)

        assertEquals(schema16Tables, emissions.next())
        emissions.cancel()
    }

    @Test
    fun committedTransactionEmitsOnceAfterBothWrites() = runBlocking {
        val emissions = RoomInvalidationSource(database).changes.produceIn(this)
        emissions.next()
        val invalidations = mutableListOf<Set<String>>()
        val observer = recordingObserver(invalidations)
        database.invalidationTracker.addObserver(observer)

        try {
            database.runInTransaction {
                database.tasks().putStats(StatsEntity(10))
                database.tasks().putStats(StatsEntity(20))
            }
            database.invalidationTracker.refreshVersionsSync()

            assertEquals(setOf("stats"), emissions.next())
            assertEquals(listOf(setOf("stats")), invalidations)
        } finally {
            database.invalidationTracker.removeObserver(observer)
            emissions.cancel()
        }
    }

    @Test
    fun rolledBackTransactionDoesNotInvalidateTheObservedTable() {
        val invalidations = mutableListOf<Set<String>>()
        val observer = recordingObserver(invalidations)
        database.invalidationTracker.addObserver(observer)

        try {
            try {
                database.runInTransaction {
                    database.tasks().putStats(StatsEntity(99))
                    throw ExpectedRollback()
                }
            } catch (_: ExpectedRollback) {
                // Expected: the write and its invalidation must roll back together.
            }
            database.invalidationTracker.refreshVersionsSync()

            assertEquals(emptyList<Set<String>>(), invalidations)
            assertNull(database.tasks().stats())
        } finally {
            database.invalidationTracker.removeObserver(observer)
        }
    }

    private fun recordingObserver(invalidations: MutableList<Set<String>>) =
        object : InvalidationTracker.Observer("stats") {
            override fun onInvalidated(tables: Set<String>) {
                invalidations += tables
            }
        }

    private suspend fun <T> ReceiveChannel<T>.next(): T = withTimeout(5_000) { receive() }

    private class ExpectedRollback : RuntimeException()
}
