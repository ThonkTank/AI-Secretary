package de.thonktank.autosecretary

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy
import de.thonktank.autosecretary.domain.model.FlowGraphEditProblem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlowEditorPersistenceViewModelTest {
    @Test fun saveIsSingleFlightAndDoesNotAcceptDraftChangesDuringItsTransaction() {
        val gateway = MemoryGateway()
        val worker = QueuedWorker()
        val vm = FlowEditorViewModel(ApplicationProvider.getApplicationContext(), FlowEditorDraft.empty(), SavedStateHandle(), gateway, worker)
        assertTrue(vm.state.value.loading)
        worker.runNext(); idle()
        vm.next(); vm.save(); vm.save(); vm.rename("Must not replace pending input")
        assertTrue(vm.state.value.saving)
        assertEquals(1, worker.jobs.size)
        worker.runNext(); idle()
        assertEquals(1, gateway.writes)
        assertEquals("Wäsche", gateway.savedNames.single())
        assertEquals("saved-task", vm.state.value.savedTaskId)
        assertFalse(vm.state.value.saving)
        clear(vm)
    }

    @Test fun recoveryBeforeCommitProtectsAndCompletesThePendingRequest() {
        verifyPendingRecovery(committedBeforeDeath = false)
    }

    @Test fun processDeathAfterCommitReplaysTheSamePersistedRequestWithoutCreatingADuplicate() {
        verifyPendingRecovery(committedBeforeDeath = true)
    }

    private fun verifyPendingRecovery(committedBeforeDeath: Boolean) {
        val gateway = MemoryGateway()
        val worker = QueuedWorker()
        val handle = SavedStateHandle()
        val vm = FlowEditorViewModel(ApplicationProvider.getApplicationContext(), FlowEditorDraft.empty(), handle, gateway, worker)
        worker.runNext(); idle(); vm.save()
        vm.checkpoint()
        val savedBeforeAcknowledgement = parcel(handle)
        if (committedBeforeDeath) worker.runNext() // Delay the main-thread acknowledgement.
        clear(vm)
        val restoredWorker = QueuedWorker()
        val restored = FlowEditorViewModel(ApplicationProvider.getApplicationContext(), FlowEditorDraft.empty(), savedBeforeAcknowledgement, gateway, restoredWorker)
        assertFalse(restored.state.value.canDiscard)
        assertFalse(restored.discardSession())
        restoredWorker.runNext() // The file has been read, but its pending request is not visible yet.
        assertFalse(restored.discardSession())
        idle()
        assertTrue(restored.state.value.saving)
        assertFalse(restored.discardSession())
        restoredWorker.runNext(); idle()
        assertEquals(1, gateway.writes)
        assertEquals(if (committedBeforeDeath) 2 else 1, gateway.requestKeys.size)
        assertEquals(1, gateway.requestKeys.distinct().size)
        assertEquals("saved-task", restored.state.value.savedTaskId)
        assertEquals(1, gateway.loads)
        assertTrue(restored.discardSession())
        assertNull(savedBeforeAcknowledgement.get<Bundle>("flow_tile_editor"))
        clear(restored)
    }

    @Test fun interruptedFileCheckpointPreventsDatabaseSaveUntilRetry() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val gateway = MemoryGateway()
        val worker = QueuedWorker()
        val handle = SavedStateHandle()
        val vm = FlowEditorViewModel(app, FlowEditorDraft.empty(), handle, gateway, worker)
        worker.runNext(); idle(); vm.checkpoint()
        val id = requireNotNull(handle.get<Bundle>("flow_tile_editor")!!.getString("sessionId"))
        val directory = File(app.noBackupFilesDir, "flow-editor/$id")
        val backup = File(directory.parentFile, "$id-backup")
        assertTrue(directory.renameTo(backup))
        try {
            assertTrue(directory.createNewFile())
            vm.save(); worker.runNext(); idle()
            assertTrue(gateway.requestKeys.isEmpty())
            assertTrue(vm.state.value.savePending)
            assertFalse(vm.discardSession())
            vm.checkpoint()
            assertTrue(directory.delete())
            assertTrue(backup.renameTo(directory))
            vm.retrySave(); worker.runNext(); idle()
            assertEquals(1, gateway.writes)
            assertEquals("saved-task", vm.state.value.savedTaskId)
            assertTrue(vm.discardSession())
        } finally {
            clear(vm)
            backup.deleteRecursively()
        }
    }

    @Test fun failedLegacyImportKeepsInlineInputUntilItsRetryIsDurable() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val root = java.nio.file.Files.createTempDirectory(app.cacheDir.toPath(), "legacy-editor").toFile()
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = root
        }
        val draft = FlowEditorDraft.empty().rename("Alter Entwurf")
        val legacy = Bundle().apply { putBundle("draft", draft.toBundle()) }
        val handle = SavedStateHandle(mapOf("flow_tile_editor" to legacy))
        val worker = QueuedWorker()
        val blocker = File(root, "flow-editor")
        assertTrue(blocker.createNewFile())
        val vm = FlowEditorViewModel(context, FlowEditorDraft.empty(), handle, MemoryGateway(), worker)
        try {
            worker.runNext()
            vm.checkpoint()
            assertNotNull(parcel(handle).get<Bundle>("flow_tile_editor")!!.getBundle("draft"))
            idle()
            assertTrue(vm.state.value.loadFailed)
            assertTrue(vm.state.value.canDiscard)
            assertTrue(blocker.delete())
            vm.retryLoad()
            assertFalse(vm.state.value.canDiscard)
            worker.runNext()
            vm.checkpoint() // Android saves the host before it processes the import callback.
            assertEquals(setOf("version", "sessionId"), parcel(handle).get<Bundle>("flow_tile_editor")!!.keySet())
            idle()
            assertEquals("Alter Entwurf", vm.state.value.draft.name)
            assertTrue(vm.discardSession())
        } finally {
            clear(vm)
            root.deleteRecursively()
        }
    }

    @Test fun aSavedUnsavedFormIsNeverOverwrittenByLoadingTheRepositoryAgain() {
        val gateway = MemoryGateway()
        val worker = QueuedWorker()
        val handle = SavedStateHandle()
        val vm = FlowEditorViewModel(ApplicationProvider.getApplicationContext(), FlowEditorDraft.empty(), handle, gateway, worker)
        worker.runNext(); idle()
        vm.openStep(vm.state.value.draft.steps.single().id); vm.field("name", "Noch nicht gespeichert")
        vm.checkpoint()
        val saved = parcel(handle); clear(vm)
        val restoredWorker = QueuedWorker()
        val restored = FlowEditorViewModel(ApplicationProvider.getApplicationContext(), FlowEditorDraft.empty(), saved, gateway, restoredWorker)
        restoredWorker.runNext(); idle()
        assertTrue(restoredWorker.jobs.isEmpty())
        assertEquals("Noch nicht gespeichert", restored.state.value.form!!.fields["name"])
        assertEquals(0, gateway.writes)
        clear(restored)
    }

    @Test fun uncertainFailureRetainsTheImmutableRequestUntilRetryResolvesIt() {
        val gateway = MemoryGateway().apply { saveFailure = IllegalStateException("Storage unavailable") }
        val worker = QueuedWorker()
        val vm = FlowEditorViewModel(ApplicationProvider.getApplicationContext(), FlowEditorDraft.empty(), SavedStateHandle(), gateway, worker)
        worker.runNext(); idle(); vm.save(); worker.runNext(); idle()
        assertTrue(vm.state.value.savePending)
        assertFalse(vm.state.value.saving)
        assertNotNull(vm.state.value.error)
        vm.rename("Not safe yet")
        assertEquals("Wäsche", vm.state.value.draft.name)
        gateway.saveFailure = null
        vm.retrySave(); worker.runNext(); idle()
        assertEquals(gateway.requestKeys[0], gateway.requestKeys[1])
        assertEquals("saved-task", vm.state.value.savedTaskId)
        clear(vm)
    }

    @Test fun transactionalValidationOpensTheAffectedResourceAndAllowsCorrection() {
        val gateway = MemoryGateway()
        val capacity = gateway.draft.capacities.addResource("Maschine", 1)
        gateway.draft = gateway.draft.withCapacities(capacity)
        val key = capacity.resources.single().key
        gateway.saveFailure = FlowGraphEditProblem("resource", key, "Kapazitätsname existiert bereits")
        val worker = QueuedWorker()
        val vm = FlowEditorViewModel(ApplicationProvider.getApplicationContext(), FlowEditorDraft.empty(), SavedStateHandle(), gateway, worker)
        worker.runNext(); idle(); vm.next(); vm.save(); worker.runNext(); idle()
        assertFalse(vm.state.value.savePending)
        assertEquals(2, vm.state.value.page)
        assertEquals("resource", vm.state.value.form!!.kind)
        assertEquals(key, vm.state.value.form!!.id)
        vm.field("name", "Andere Maschine")
        vm.submitForm()
        assertEquals("Andere Maschine", vm.state.value.draft.capacities.resources.single().name)
        clear(vm)
    }

    @Test fun failedLoadDoesNotTurnAnExistingFlowIntoAnEditableEmptyDraft() {
        val gateway = MemoryGateway().apply { loadFailure = IllegalStateException("Unavailable") }
        val worker = QueuedWorker()
        val vm = FlowEditorViewModel(ApplicationProvider.getApplicationContext(), FlowEditorDraft.empty(), SavedStateHandle(), gateway, worker, "existing-flow")
        worker.runNext(); idle()
        assertTrue(vm.state.value.loadFailed)
        vm.rename("Must not save"); vm.openStep(null); vm.save()
        assertTrue(vm.state.value.draft.name.isEmpty())
        assertNull(vm.state.value.form)
        assertTrue(worker.jobs.isEmpty())
        gateway.loadFailure = null
        vm.retryLoad(); worker.runNext(); idle()
        assertFalse(vm.state.value.loadFailed)
        assertEquals(listOf("existing-flow", "existing-flow"), gateway.loadedIds)
        clear(vm)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun clear(vm: FlowEditorViewModel) { ViewModelStore().apply { put("editor", vm); clear() } }
    private fun parcel(handle: SavedStateHandle): SavedStateHandle {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(handle.get<Bundle>("flow_tile_editor")); parcel.setDataPosition(0)
            SavedStateHandle(mapOf("flow_tile_editor" to parcel.readBundle(javaClass.classLoader)))
        } finally { parcel.recycle() }
    }

    private class MemoryGateway : FlowEditorGateway {
        var draft = FlowEditorDraft.empty().rename("Wäsche").addStep("Waschen", FlowDelayPolicy.fixed(0))
        var loads = 0
        var writes = 0
        var saveFailure: RuntimeException? = null
        var loadFailure: RuntimeException? = null
        val receipts = mutableMapOf<String, String>()
        val requestKeys = mutableListOf<String>()
        val savedNames = mutableListOf<String>()
        val loadedIds = mutableListOf<String?>()
        override fun load(taskId: String?): FlowEditorDraft {
            loads++; loadedIds += taskId; loadFailure?.let { throw it }; return draft
        }
        override fun save(draft: FlowEditorDraft, requestKey: String): String {
            requestKeys += requestKey
            saveFailure?.let { throw it }
            return receipts.getOrPut(requestKey) { writes++; savedNames += draft.name; "saved-task" }
        }
    }

    private class QueuedWorker : AbstractExecutorService() {
        val jobs = ArrayDeque<Runnable>()
        private var stopped = false
        fun runNext() { jobs.removeFirst().run() }
        override fun execute(command: Runnable) { check(!stopped); jobs.addLast(command) }
        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> { stopped = true; return jobs.toMutableList().also { jobs.clear() } }
        override fun isShutdown() = stopped
        override fun isTerminated() = stopped && jobs.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
    }
}
