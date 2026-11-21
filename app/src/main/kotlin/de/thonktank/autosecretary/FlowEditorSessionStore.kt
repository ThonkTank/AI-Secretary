package de.thonktank.autosecretary

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** A structural edit gets a new identity. A title change keeps the identity and changes only the title. */
data class FlowSessionRevision(val id: String, val draft: FlowEditorDraft) {
    companion object {
        fun fresh(draft: FlowEditorDraft) = FlowSessionRevision(UUID.randomUUID().toString(), draft)
    }
}

data class FlowSessionSnapshot(
    val current: FlowSessionRevision,
    val history: List<FlowSessionRevision> = emptyList(),
    val page: Int = 1,
    val form: FlowEditorForm? = null,
    val error: String? = null,
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    val loadId: String? = null,
    val pendingKey: String? = null,
    val pending: FlowSessionRevision? = null,
    val savedTaskId: String? = null,
) {
    init {
        require(history.size <= 100)
        require((pendingKey == null) == (pending == null))
    }
}

/** App-private recovery for a single editor. Only the session id crosses the Activity boundary. */
class FlowEditorSessionStore internal constructor(root: File, sessionId: String) {
    constructor(context: Context, sessionId: String) : this(File(context.noBackupFilesDir, "flow-editor"), sessionId)

    private val directory = File(root, canonicalSessionId(sessionId))
    private val head = File(directory, "head")
    private val monitor = Any()
    private var queued: FlowSessionSnapshot? = null
    private var writer: Future<*>? = null
    private var running = false
    private var closed = false
    private var failure: IOException? = null

    // Accessed only under diskLock. Generation prevents an old host from replacing a newer head.
    private var generation = 0L
    private var ownerConflict = false
    private val stored = mutableSetOf<String>()

    /** Enqueues immutable state. Rapid input can replace a write that has not started yet. */
    fun schedule(snapshot: FlowSessionSnapshot) {
        val captured = snapshot.copy(history = snapshot.history.toList(),
            form = snapshot.form?.let { it.copy(fields = it.fields.toMap()) })
        synchronized(monitor) {
            check(!closed) { "Editor session closed" }
            queued = captured
            if (!running) {
                running = true
                writer = executor.submit(::drain)
            }
        }
    }

    private fun drain() {
        while (true) {
            val next = synchronized(monitor) {
                val item = queued
                queued = null
                if (item == null) {
                    running = false
                    return
                }
                item
            }
            val problem = try {
                synchronized(diskLock) { write(next) }
                null
            } catch (e: Exception) {
                IOException("Ablaufentwurf konnte nicht gesichert werden", e)
            }
            synchronized(monitor) { failure = problem }
        }
    }

    /** Waits until previously scheduled edits are durable, or reports the write failure. */
    @Throws(IOException::class)
    fun checkpoint() {
        val job = synchronized(monitor) { writer } ?: return
        try {
            job.get()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Sichern des Ablaufentwurfs unterbrochen", e)
        } catch (e: ExecutionException) {
            throw IOException("Sichern des Ablaufentwurfs fehlgeschlagen", e.cause)
        }
        synchronized(monitor) { failure?.let { throw it } }
    }

    /** Reads an existing session. Missing or unreadable data must never become an editable empty draft. */
    @Throws(IOException::class)
    fun restore(): FlowSessionSnapshot {
        checkpoint()
        return synchronized(diskLock) {
            val revisions = mutableMapOf<String, FlowEditorDraft>()
            val (version, state) = FlowSessionCodec.readHead(head) { id, title ->
                val structure = revisions.getOrPut(id) { FlowSessionCodec.readDraft(revisionFile(id)) }
                FlowSessionRevision(id, structure.rename(title))
            }
            generation = version
            ownerConflict = false
            stored.clear()
            stored += revisions.keys
            state
        }
    }

    /** Finishes queued work without deleting data during rotation or host destruction. */
    @Throws(IOException::class)
    fun close() {
        synchronized(monitor) { closed = true }
        checkpoint()
    }

    /** Explicit discard or acknowledged completion. A failed write may also be explicitly discarded. */
    @Throws(IOException::class)
    fun discard() {
        synchronized(monitor) { closed = true; queued = null }
        try {
            checkpoint()
        } catch (e: IOException) {
            if (Thread.currentThread().isInterrupted) throw e
        }
        synchronized(diskLock) {
            if (!directory.exists()) return
            if ((ownerConflict || generation != 0L) && head.exists()) checkOwner()
            if (!directory.deleteRecursively()) throw IOException("Ablaufentwurf konnte nicht verworfen werden")
            stored.clear()
        }
    }

    private fun write(state: FlowSessionSnapshot) {
        Files.createDirectories(directory.toPath())
        checkOwner()
        val retained = mutableSetOf<String>()
        fun retain(revision: FlowSessionRevision) {
            if (!retained.add(revision.id) || revision.id in stored) return
            replaceAtomically(revisionFile(revision.id), FlowSessionCodec.encodeDraft(revision.draft))
            stored += revision.id
        }
        retain(state.current)
        state.history.forEach(::retain)
        state.pending?.let(::retain)
        val next = generation + 1
        replaceAtomically(head, FlowSessionCodec.encodeHead(next, state))
        generation = next
        // The new head no longer needs these undo steps. A cleanup failure leaves a harmless orphan.
        val unused = stored - retained
        for (id in unused) {
            val file = revisionFile(id)
            if (file.delete() || !file.exists()) stored.remove(id)
            else Log.w("FlowEditor", "Unable to remove unused editor revision")
        }
    }

    private fun checkOwner() {
        val onDisk = if (head.exists()) FlowSessionCodec.generation(head) else 0L
        if (onDisk != generation) {
            ownerConflict = true
            throw IOException("Ablaufentwurf wurde von einer anderen Sitzung geändert")
        }
    }

    private fun revisionFile(id: String) = File(directory, "${canonicalSessionId(id)}.draft")

    private fun replaceAtomically(file: File, contents: ByteArray) {
        val temp = File.createTempFile("checkpoint-", ".tmp", directory)
        try {
            FileOutputStream(temp).use { stream -> stream.write(contents); stream.fd.sync() }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (temp.exists() && !temp.delete()) Log.w("FlowEditor", "Unable to remove temporary editor checkpoint")
        }
    }

    companion object {
        private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "flow-editor-checkpoint").apply { isDaemon = true } }
        private val diskLock = Any()
        internal fun canonicalSessionId(value: String): String {
            require(UUID.fromString(value).toString() == value) { "Ungültige Entwurfskennung" }
            return value
        }
    }
}
