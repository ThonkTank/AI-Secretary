package de.thonktank.autosecretary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy
import de.thonktank.autosecretary.domain.model.FlowTileGraph
import de.thonktank.autosecretary.presentation.editor.FlowTileEditorScreen
import java.time.LocalTime

/** Test-only Android host, never registered or packaged in the production variant. */
class FlowTileEditorHarnessActivity : ComponentActivity() {
    lateinit var editor: FlowEditorViewModel
        private set
    private lateinit var initialDraft: FlowEditorDraft
    private var generation = 0
    private var revision by mutableIntStateOf(0)
    var saves = 0
        private set
    var fontScale by mutableFloatStateOf(1f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        generation = savedInstanceState?.getInt("generation") ?: 0
        initialDraft = savedInstanceState?.getBundle("initialDraft")?.let(FlowEditorDraft::fromBundle)
            ?: reference()
        editor = obtainEditor()
        setContent {
            @Suppress("UNUSED_EXPRESSION") revision
            val state by editor.state.collectAsState()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    FlowTileEditorScreen(state, DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT),
                        editor, { saves++ }, { finish() })
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("generation", generation)
        outState.putBundle("initialDraft", initialDraft.toBundle())
        super.onSaveInstanceState(outState)
    }

    fun render(draft: FlowEditorDraft) {
        initialDraft = draft
        generation++
        editor = obtainEditor()
        revision++
    }

    private fun obtainEditor(): FlowEditorViewModel = ViewModelProvider(this,
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                require(modelClass.isAssignableFrom(FlowEditorViewModel::class.java))
                return FlowEditorViewModel(initialDraft, extras.createSavedStateHandle()) as T
            }
        })["flow-test-editor-$generation", FlowEditorViewModel::class.java]

    private fun reference(): FlowEditorDraft {
        var draft = FlowEditorDraft.empty().rename("Wäsche")
        listOf("Waschen", "Aufhängen", "Trockner").forEach { draft = draft.addStep(it, FlowDelayPolicy.fixed(0)) }
        val ids = draft.steps.map { it.id }
        return draft.withGraph(FlowTileGraph(ids, listOf(FlowTileGraph.Link(ids[0], ids[1]))))
    }
}
