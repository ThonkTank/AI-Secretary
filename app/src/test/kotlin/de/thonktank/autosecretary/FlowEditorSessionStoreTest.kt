package de.thonktank.autosecretary

import de.thonktank.autosecretary.domain.model.FlowDelayPolicy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class FlowEditorSessionStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val id = UUID.randomUUID().toString()
    private fun store() = FlowEditorSessionStore(temporary.root, id)
    private fun directory() = File(temporary.root, id)

    @Test fun inputChangesReuseStructureAndRecoverRawTextHistoryAndFrozenSave() {
        val store = store()
        val before = FlowSessionRevision.fresh(FlowEditorDraft.empty().rename("Anfang"))
        val wait = FlowDelayPolicy.rememberLast(86_400_000L).remember(2_400_000_000L)
        val step = before.draft.appendStep("Änderung", wait)
        val stepId = step.steps.single().id
        val resources = step.capacities.addResource("Spüle", 2)
        val capacities = resources.addLease(resources.resources.single().key, stepId, stepId, 2)
        val leaseId = capacities.leases.single().key
        val structure = step.withCapacities(capacities).releaseAfter(leaseId, true)
        val current = FlowSessionRevision.fresh(structure)
        store.schedule(FlowSessionSnapshot(current, history = listOf(before)))
        store.checkpoint()
        val revision = File(directory(), "${current.id}.draft")
        assertTrue(revision.setLastModified(1_600_000_000_000L))
        val text = " roh 🪴, ".repeat(9000) + '\uD83D'
        val fields = mutableMapOf("duration" to text)
        val renamed = current.copy(draft = structure.rename("  Titel 🪴  "))
        store.schedule(FlowSessionSnapshot(renamed, listOf(before), page = 2,
            form = FlowEditorForm("step", stepId, fields), error = "Eingabe prüfen", loadId = "existing"))
        fields["duration"] = "changed after enqueue"
        store.close()

        val reopened = store()
        val recovered = reopened.restore()
        assertEquals(1_600_000_000_000L, revision.lastModified())
        assertEquals(2, directory().listFiles()!!.count { it.extension == "draft" })
        assertEquals("  Titel 🪴  ", recovered.current.draft.name)
        assertEquals(text, recovered.form!!.fields["duration"])
        assertEquals("Anfang", recovered.history.single().draft.name)
        assertEquals(2, recovered.page)
        assertEquals("existing", recovered.loadId)
        assertEquals("Eingabe prüfen", recovered.error)
        assertEquals(wait, recovered.current.draft.waits.getValue(stepId))
        assertEquals(true, recovered.current.draft.releaseAfterWait[leaseId])
        assertEquals(2, recovered.current.draft.capacities.leases.single().units)
        assertEquals(resources.resources.single().key, recovered.current.draft.capacities.resources.single().key)
        assertEquals(structure.nextIdentity, recovered.current.draft.nextIdentity)
        reopened.schedule(recovered.copy(pendingKey = "request", pending = recovered.current, form = null))
        reopened.checkpoint()
        val replay = store().restore()
        assertEquals("request", replay.pendingKey)
        assertEquals(replay.current.id, replay.pending!!.id)
        assertEquals("  Titel 🪴  ", replay.pending.draft.name)
        reopened.discard()
        assertFalse(directory().exists())
    }

    @Test fun failedWritePreservesPreviousHeadAndCanBeRetried() {
        val store = store()
        val initial = FlowSessionRevision.fresh(FlowEditorDraft.empty().rename("Old"))
        store.schedule(FlowSessionSnapshot(initial)); store.checkpoint()
        val next = FlowSessionRevision.fresh(initial.draft.appendStep("Next", FlowDelayPolicy.fixed(0)))
        // Prevent the atomic rename of the new revision while the old head is still usable.
        val blocker = File(directory(), "${next.id}.draft")
        assertTrue(blocker.mkdir())
        store.schedule(FlowSessionSnapshot(next, listOf(initial)))
        assertThrows(IOException::class.java) { store.checkpoint() }
        assertEquals(initial.id, store().restore().current.id)
        assertTrue(blocker.delete())
        store.schedule(FlowSessionSnapshot(next, listOf(initial))); store.checkpoint()
        assertEquals(next.id, store().restore().current.id)
        store.discard()
    }

    @Test fun droppingUndoStepsReclaimsOnlyUnreferencedRevisions() {
        val store = store()
        val revisions = (0..101).map { FlowSessionRevision.fresh(FlowEditorDraft.empty().rename("$it")) }
        store.schedule(FlowSessionSnapshot(revisions[100], revisions.take(100))); store.checkpoint()
        store.schedule(FlowSessionSnapshot(revisions[101], revisions.subList(1, 101))); store.checkpoint()
        assertEquals(101, directory().listFiles()!!.count { it.extension == "draft" })
        val restored = store().restore()
        assertEquals((1..100).map(Int::toString), restored.history.map { it.draft.name })
        assertEquals("101", restored.current.draft.name)
        store.discard()
    }

    @Test fun missingAndDamagedDataCannotRestoreAsAnEmptyDraft() {
        val store = store()
        assertThrows(IOException::class.java) { store.restore() }
        val draft = FlowSessionRevision.fresh(FlowEditorDraft.empty())
        store.schedule(FlowSessionSnapshot(draft)); store.checkpoint()
        val file = File(directory(), "${draft.id}.draft")
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertThrows(IOException::class.java) { store().restore() }
        store.discard()
    }

    @Test fun failedRestoreCannotDiscardASubsequentlyRepairedSession() {
        val writer = store()
        val broken = FlowSessionRevision.fresh(FlowEditorDraft.empty())
        writer.schedule(FlowSessionSnapshot(broken)); writer.checkpoint()
        assertTrue(File(directory(), "${broken.id}.draft").delete())
        val reader = store()
        assertThrows(IOException::class.java) { reader.restore() }
        val fixed = FlowSessionRevision.fresh(FlowEditorDraft.empty().rename("Gerettet"))
        writer.schedule(FlowSessionSnapshot(fixed)); writer.checkpoint()
        assertThrows(IOException::class.java) { reader.discard() }
        assertEquals("Gerettet", store().restore().current.draft.name)

        File(directory(), "head").writeText("defekt")
        val damaged = store()
        assertThrows(IOException::class.java) { damaged.restore() }
        damaged.discard()
        assertFalse(directory().exists())
    }

    @Test fun aStaleWriterCannotOverwriteOrDiscardANewerCheckpoint() {
        val first = store()
        val draft = FlowSessionRevision.fresh(FlowEditorDraft.empty())
        first.schedule(FlowSessionSnapshot(draft)); first.checkpoint()
        val second = store()
        second.schedule(second.restore().copy(error = "newer")); second.checkpoint()
        first.schedule(FlowSessionSnapshot(draft, error = "stale"))
        assertThrows(IOException::class.java) { first.checkpoint() }
        assertThrows(IOException::class.java) { first.discard() }
        assertEquals("newer", store().restore().error)
        second.discard()
    }
}
