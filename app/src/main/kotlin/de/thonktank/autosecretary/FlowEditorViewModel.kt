package de.thonktank.autosecretary

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy
import de.thonktank.autosecretary.domain.model.FlowTileGraph
import de.thonktank.autosecretary.domain.model.FlowGraphEditProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class FlowEditorForm(val kind: String, val id: String?, val fields: Map<String, String>)
data class FlowEditorState(
    val draft: FlowEditorDraft,
    val page: Int = 1,
    val form: FlowEditorForm? = null,
    val error: String? = null,
    val canUndo: Boolean = false,
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    val saving: Boolean = false,
    val savePending: Boolean = false,
    val savedTaskId: String? = null,
)

/** Owns the complete unsaved editor, including open forms and undo across process recreation. */
class FlowEditorViewModel(
    initial: FlowEditorDraft,
    private val savedState: SavedStateHandle,
    private val gateway: FlowEditorGateway? = null,
    private val worker: ExecutorService? = gateway?.let { Executors.newSingleThreadExecutor() },
    initialLoadId: String? = initial.taskId,
) : ViewModel() {
    private val history = mutableListOf<FlowEditorDraft>()
    private val mutableState = MutableStateFlow(FlowEditorState(initial))
    val state: StateFlow<FlowEditorState> = mutableState
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var loadId: String? = initialLoadId
    private var pendingKey: String? = null
    private var pendingDraft: FlowEditorDraft? = null

    init {
        val restored = savedState.get<Bundle>(KEY)
        restored?.let { restore(it) }
        persist()
        if (gateway != null && state.value.savedTaskId == null) {
            if (pendingKey != null) performSave()
            else if (restored == null || state.value.loading) load()
        }
    }

    fun rename(value: String) { if (canEdit()) publish(state.value.copy(draft = state.value.draft.rename(value))) }

    fun openStep(id: String?) {
        if (!canEdit()) return
        val wait = id?.let { state.value.draft.waits.getValue(it) } ?: FlowDelayPolicy.fixed(0)
        val millis = wait.proposedDelayMillis()
        val unit = when {
            millis > 0 && millis % DAY == 0L -> DAY
            millis > 0 && millis % HOUR == 0L -> HOUR
            else -> MINUTE
        }
        open(FlowEditorForm("step", id, mapOf(
            "name" to (id?.let { state.value.draft.step(it).text } ?: ""),
            "duration" to BigDecimal.valueOf(millis).divide(BigDecimal.valueOf(unit), 9, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(),
            "unit" to unit.toString(), "ask" to (wait.mode == FlowDelayPolicy.Mode.REMEMBER_LAST).toString(),
        )))
    }

    fun openResource(id: String?) {
        if (!canEdit()) return
        val resource = id?.let { state.value.draft.capacities.resource(it) }
        open(FlowEditorForm("resource", id, mapOf("name" to (resource?.name ?: ""),
            "total" to (resource?.capacity ?: 1).toString())))
    }

    fun openLease(id: String? = null) {
        if (!canEdit()) return
        val draft = state.value.draft
        if (draft.steps.isEmpty() || draft.capacities.resources.isEmpty()) return
        val lease = draft.capacities.leases.find { it.key == id }
        open(FlowEditorForm("lease", id, mapOf(
            "resource" to (lease?.resourceKey ?: draft.capacities.resources.first().key),
            "quantity" to (lease?.units ?: 1).toString(),
            "from" to (lease?.acquireStepId ?: draft.steps.first().id),
            "to" to (lease?.releaseStepId ?: draft.steps.last().id),
            "afterWait" to (draft.releaseAfterWait[id] == true).toString(),
        )))
    }

    fun openCadence(id: String) {
        if (!canEdit()) return
        if (!state.value.draft.graph.roots().contains(id)) return
        open(FlowEditorForm("cadence", id, mapOf("days" to (state.value.draft.step(id).intervalDays ?: 1).toString())))
    }

    fun field(key: String, value: String) {
        if (!canEdit()) return
        val form = state.value.form ?: return
        publish(state.value.copy(form = form.copy(fields = form.fields + (key to value)), error = null))
    }

    fun closeForm() { if (canEdit()) publish(state.value.copy(form = null, error = null)) }

    fun submitForm() = guarded {
        val form = state.value.form ?: return@guarded
        val fields = form.fields
        val draft = state.value.draft
        val next = when (form.kind) {
            "step" -> {
                val duration = BigDecimal(fields.getValue("duration").replace(',', '.'))
                    .multiply(BigDecimal(fields.getValue("unit"))).setScale(0, RoundingMode.HALF_UP).longValueExact()
                val previous = form.id?.let { draft.waits.getValue(it) }
                val ask = fields["ask"] == "true"
                val wait = if (previous != null && previous.proposedDelayMillis() == duration &&
                    (previous.mode == FlowDelayPolicy.Mode.REMEMBER_LAST) == ask) previous
                    else if (ask) FlowDelayPolicy.rememberLast(duration) else FlowDelayPolicy.fixed(duration)
                if (form.id == null) draft.addStep(fields.getValue("name"), wait)
                else draft.editStep(form.id, fields.getValue("name"), wait)
            }
            "resource" -> draft.withCapacities(if (form.id == null)
                draft.capacities.addResource(fields.getValue("name"), fields.getValue("total").toInt())
                else draft.capacities.updateResource(form.id, fields.getValue("name"), fields.getValue("total").toInt()))
            "lease" -> {
                val from = fields.getValue("from")
                val to = fields.getValue("to")
                require(draft.graph.reaches(from, to)) { "Freigabe muss am selben oder einem folgenden Schritt liegen" }
                val resource = fields.getValue("resource")
                val quantity = fields.getValue("quantity").toInt()
                require(quantity in 1..draft.capacities.resource(resource).capacity) { "Benötigte Menge überschreitet die Kapazität" }
                val capacities = if (form.id == null) draft.capacities.addLease(resource, from, to, quantity)
                    else draft.capacities.updateLease(form.id, resource, from, to, quantity)
                val leaseId = form.id ?: capacities.leases.last().key
                draft.withCapacities(capacities).releaseAfter(leaseId, fields["afterWait"] == "true")
            }
            "cadence" -> draft.cadence(requireNotNull(form.id), fields.getValue("days").toInt())
            else -> error("Unbekannte Bearbeitung")
        }
        commit(next)
    }

    fun place(source: String, target: String, placement: FlowTileGraph.Placement, branch: Boolean = false) = guarded {
        commit(state.value.draft.withGraph(state.value.draft.graph.place(source, target, placement, branch)))
    }

    fun join(ends: List<String>, successor: String) = guarded {
        commit(state.value.draft.withGraph(state.value.draft.graph.join(ends, successor)))
    }

    fun undo() {
        if (!canEdit()) return
        if (history.isEmpty()) return
        publish(state.value.copy(draft = history.removeAt(history.lastIndex), form = null,
            error = null, canUndo = history.isNotEmpty()))
    }

    fun next() {
        if (!canEdit()) return
        if (state.value.draft.name.isBlank() || state.value.draft.steps.isEmpty()) {
            publish(state.value.copy(error = "Ablaufname und mindestens einen Schritt ergänzen"))
        } else publish(state.value.copy(page = 2, form = null, error = null))
    }

    fun back() { if (canEdit()) publish(state.value.copy(page = 1, form = null, error = null)) }

    fun prepareSave(): Boolean {
        if (!canEdit() || state.value.form != null) return false
        return try {
            state.value.draft.validateForSave()
            true
        } catch (problem: FlowEditorDraft.Problem) {
            when (problem.kind) {
                "name" -> back()
                "step" -> { back(); openStep(problem.elementId) }
                "resource" -> { publish(state.value.copy(page = 2)); openResource(problem.elementId) }
                "lease" -> { publish(state.value.copy(page = 2)); openLease(problem.elementId) }
            }
            publish(state.value.copy(error = problem.message))
            false
        }
    }

    fun save() {
        if (gateway == null || !prepareSave()) return
        pendingKey = UUID.randomUUID().toString()
        pendingDraft = state.value.draft
        performSave()
    }

    fun retrySave() { if (!closed && !state.value.saving && pendingKey != null) performSave() }
    fun retryLoad() { if (!closed && state.value.loadFailed && !state.value.loading) load() }

    private fun load() {
        val store = gateway ?: return
        publish(state.value.copy(loading = true, loadFailed = false, error = null))
        worker!!.execute {
            try {
                val loaded = store.load(loadId)
                main.post { if (!closed) publish(state.value.copy(draft = loaded, loading = false, error = null)) }
            } catch (failure: Exception) {
                Log.e("FlowEditor", "Unable to load flow draft", failure)
                main.post { if (!closed) publish(state.value.copy(loading = false, loadFailed = true,
                    error = "Ablauf konnte nicht geladen werden. Deine gespeicherten Daten bleiben unverändert.")) }
            }
        }
    }

    private fun performSave() {
        val store = gateway ?: return
        val key = pendingKey ?: return
        val draft = pendingDraft ?: return
        publish(state.value.copy(saving = true, savePending = true, error = null))
        worker!!.execute {
            try {
                val id = store.save(draft, key)
                main.post {
                    if (!closed) {
                        pendingKey = null; pendingDraft = null
                        publish(state.value.copy(saving = false, savePending = false, savedTaskId = id, error = null))
                    }
                }
            } catch (failure: Exception) {
                Log.e("FlowEditor", "Unable to complete flow save", failure)
                main.post {
                    if (!closed) {
                        if (failure is IllegalArgumentException) {
                            // Validation failed inside the save transaction, so no write committed.
                            pendingKey = null; pendingDraft = null
                            publish(state.value.copy(saving = false, savePending = false))
                            if (failure is FlowGraphEditProblem) when (failure.kind) {
                                "step" -> { back(); openStep(failure.key) }
                                "resource" -> { publish(state.value.copy(page = 2)); openResource(failure.key) }
                                "lease" -> { publish(state.value.copy(page = 2)); openLease(failure.key) }
                            }
                            publish(state.value.copy(error = failure.message ?: "Eingabe prüfen"))
                        } else publish(state.value.copy(saving = false,
                            error = "Speichern konnte nicht abgeschlossen werden. Deine Eingaben bleiben erhalten."))
                    }
                }
            }
        }
    }

    private fun canEdit() = !closed && !state.value.loading && !state.value.loadFailed &&
        !state.value.saving && !state.value.savePending && state.value.savedTaskId == null

    override fun onCleared() {
        closed = true
        worker?.shutdownNow()
        main.removeCallbacksAndMessages(null)
    }

    private fun open(form: FlowEditorForm) = publish(state.value.copy(form = form, error = null))
    private fun commit(draft: FlowEditorDraft) {
        history.add(state.value.draft)
        if (history.size > 100) history.removeAt(0)
        publish(state.value.copy(draft = draft, form = null, error = null, canUndo = true))
    }
    private fun guarded(action: () -> Unit) {
        if (!canEdit()) return
        try { action() } catch (invalid: IllegalArgumentException) {
            publish(state.value.copy(error = invalid.message ?: "Eingabe prüfen"))
        } catch (_: ArithmeticException) {
            publish(state.value.copy(error = "Gültige Wartezeit zwischen 0 und 30 Tagen eingeben"))
        }
    }
    private fun publish(value: FlowEditorState) { mutableState.value = value; persist() }
    private fun persist() {
        val value = state.value
        savedState[KEY] = Bundle().apply {
            putBundle("draft", value.draft.toBundle()); putInt("page", value.page)
            putString("error", value.error)
            putBoolean("loading", value.loading); putBoolean("loadFailed", value.loadFailed)
            putString("loadId", loadId); putString("savedTaskId", value.savedTaskId)
            putString("pendingKey", pendingKey); pendingDraft?.let { putBundle("pendingDraft", it.toBundle()) }
            value.form?.let { form -> putBundle("form", Bundle().apply {
                putString("kind", form.kind); putString("id", form.id)
                putBundle("fields", Bundle().apply { form.fields.forEach { (k, v) -> putString(k, v) } })
            }) }
            putParcelableArrayList("history", ArrayList(history.map { it.toBundle() }))
        }
    }
    @Suppress("DEPRECATION")
    private fun restore(bundle: Bundle) {
        loadId = bundle.getString("loadId", loadId)
        pendingKey = bundle.getString("pendingKey")
        pendingDraft = bundle.getBundle("pendingDraft")?.let { FlowEditorDraft.fromBundle(it) }
        bundle.getParcelableArrayList<Bundle>("history")?.forEach { history.add(FlowEditorDraft.fromBundle(it)) }
        val form = bundle.getBundle("form")?.let { value ->
            val fields = value.getBundle("fields") ?: Bundle()
            FlowEditorForm(requireNotNull(value.getString("kind")), value.getString("id"),
                fields.keySet().associateWith { fields.getString(it, "") })
        }
        mutableState.value = FlowEditorState(FlowEditorDraft.fromBundle(bundle.getBundle("draft")),
            bundle.getInt("page", 1), form, bundle.getString("error"), history.isNotEmpty(),
            loading = bundle.getBoolean("loading"), loadFailed = bundle.getBoolean("loadFailed"),
            savePending = pendingKey != null, savedTaskId = bundle.getString("savedTaskId"))
    }
    companion object {
        private const val KEY = "flow_tile_editor"
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
