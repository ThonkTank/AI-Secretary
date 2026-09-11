package de.thonktank.autosecretary

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import de.thonktank.autosecretary.presentation.editor.FlowTileEditorScreen

/** The single creation/editing host for explicit flows; the ViewModel owns the entire draft. */
class FlowSetupActivity : ComponentActivity() {
    private lateinit var editor: FlowEditorViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AutoSecretaryApplication.from(this).container()
        val gateway = UseCaseFlowEditorGateway(container.flows.loadGraph, container.flows.saveGraph)
        editor = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                require(modelClass.isAssignableFrom(FlowEditorViewModel::class.java))
                return FlowEditorViewModel(FlowEditorDraft.empty(), extras.createSavedStateHandle(),
                    gateway, initialLoadId = intent.getStringExtra(TASK_ID)) as T
            }
        })[FlowEditorViewModel::class.java]
        setContent {
            val state by editor.state.collectAsState()
            LaunchedEffect(state.savedTaskId) {
                state.savedTaskId?.let {
                    setResult(RESULT_OK, Intent().putExtra(TASK_ID, it))
                    container.executors.timerSerial.execute { container.flowWakeScheduler.reschedule() }
                    finish()
                }
            }
            BackHandler {
                when {
                    state.saving -> Unit
                    state.form != null -> editor.closeForm()
                    state.page == 2 -> editor.back()
                    else -> confirmDiscard()
                }
            }
            FlowTileEditorScreen(state, DayPalette.at(container.clock.time(), DayPalette.Mode.AUTO),
                editor, editor::save, ::finish,
                Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding())
        }
    }

    private fun confirmDiscard() {
        android.app.AlertDialog.Builder(this).setMessage("Änderungen verwerfen?")
            .setNegativeButton("Weiter bearbeiten", null)
            .setPositiveButton("Verwerfen") { _, _ -> finish() }.show()
    }

    companion object { const val TASK_ID = "flow_task_id" }
}
