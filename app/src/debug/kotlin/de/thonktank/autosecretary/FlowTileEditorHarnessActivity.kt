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
import androidx.lifecycle.SavedStateHandle
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy
import de.thonktank.autosecretary.domain.model.FlowTileGraph
import de.thonktank.autosecretary.presentation.editor.FlowTileEditorScreen
import java.time.LocalTime

/** Test-only Android host, never registered or packaged in the production variant. */
class FlowTileEditorHarnessActivity : ComponentActivity() {
    lateinit var editor: FlowEditorViewModel
        private set
    private lateinit var handle: SavedStateHandle
    private var revision by mutableIntStateOf(0)
    var saves = 0
        private set
    var fontScale by mutableFloatStateOf(1f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle = SavedStateHandle(savedInstanceState?.getBundle("editor")?.let { mapOf("flow_tile_editor" to it) }.orEmpty())
        editor = FlowEditorViewModel(reference(), handle)
        viewModelStore.put("flow-test-editor", editor)
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
        outState.putBundle("editor", handle.get<Bundle>("flow_tile_editor"))
        super.onSaveInstanceState(outState)
    }

    fun render(draft: FlowEditorDraft) {
        handle = SavedStateHandle()
        editor = FlowEditorViewModel(draft, handle)
        viewModelStore.put("flow-test-editor", editor)
        revision++
    }

    private fun reference(): FlowEditorDraft {
        var draft = FlowEditorDraft.empty().rename("Wäsche")
        listOf("Waschen", "Aufhängen", "Trockner").forEach { draft = draft.addStep(it, FlowDelayPolicy.fixed(0)) }
        val ids = draft.steps.map { it.id }
        return draft.withGraph(FlowTileGraph(ids, listOf(FlowTileGraph.Link(ids[0], ids[1]))))
    }
}
