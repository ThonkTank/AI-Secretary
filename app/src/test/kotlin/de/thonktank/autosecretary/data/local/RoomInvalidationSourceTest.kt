package de.thonktank.autosecretary.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.thonktank.autosecretary.AppDatabase
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomInvalidationSourceTest {
    private val directExecutor = Executor { command -> command.run() }
    private val schema20Tables = setOf(
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
        "capacity_resources",
        "step_transitions",
        "step_resource_leases",
        "step_flow_runs",
        "flow_run_steps",
        "flow_run_resources",
    )
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun initialEmissionContainsTheCompleteSchema20TableContract() = runBlocking {
        val emissions = RoomInvalidationSource(database).changes.produceIn(this)

        assertEquals(schema20Tables, emissions.next())
        emissions.cancel()
    }

    @Test
    fun committedTransactionEmitsAfterBothWrites() = runBlocking {
        val emissions = RoomInvalidationSource(database).changes.produceIn(this)
        emissions.next()

        try {
            database.runInTransaction {
                database.today().putStats(StatsEntity(10))
                database.today().putStats(StatsEntity(20))
            }

            assertEquals(setOf("stats"), emissions.next())
            assertEquals(20, database.today().stats()?.xp)
        } finally {
            emissions.cancel()
        }
    }

    @Test
    fun rolledBackTransactionDoesNotEmitOrPersistAChange() = runBlocking {
        val emissions = RoomInvalidationSource(database).changes.produceIn(this)
        emissions.next()

        try {
            try {
                database.runInTransaction {
                    database.today().putStats(StatsEntity(99))
                    throw ExpectedRollback()
                }
            } catch (_: ExpectedRollback) {
                // Expected: the write and its invalidation must roll back together.
            }
            yield()

            assertTrue(emissions.tryReceive().isFailure)
            assertNull(database.today().stats())
        } finally {
            emissions.cancel()
        }
    }

    private suspend fun <T> ReceiveChannel<T>.next(): T = withTimeout(5_000) { receive() }

    private class ExpectedRollback : RuntimeException()
}
