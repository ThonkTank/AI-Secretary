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
        if (DiagnosticBootstrap.isDiagnosing()) { finish(); return }
        val container = AutoSecretaryApplication.from(this).container()
        val gateway = UseCaseFlowEditorGateway(container.flows.loadGraph, container.flows.saveGraph)
        editor = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                require(modelClass.isAssignableFrom(FlowEditorViewModel::class.java))
                return FlowEditorViewModel(applicationContext, FlowEditorDraft.empty(), extras.createSavedStateHandle(),
                    gateway, initialLoadId = intent.getStringExtra(TASK_ID)) as T
            }
        })[FlowEditorViewModel::class.java]
        setContent {
            val state by editor.state.collectAsState()
            LaunchedEffect(state.savedTaskId) {
                if (state.savedTaskId != null) finishEditor()
            }
            BackHandler {
                when {
                    !state.canDiscard -> Unit
                    state.form != null -> editor.closeForm()
                    state.page == 2 -> editor.back()
                    else -> confirmDiscard()
                }
            }
            FlowTileEditorScreen(state, DayPalette.at(container.clock.time(), DayPalette.Mode.AUTO),
                editor, editor::save, ::finishEditor,
                Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::editor.isInitialized) editor.checkpoint()
        super.onSaveInstanceState(outState)
    }

    private fun finishEditor() {
        if (isFinishing || !editor.state.value.canDiscard) return
        editor.state.value.savedTaskId?.let { taskId ->
            setResult(RESULT_OK, Intent().putExtra(TASK_ID, taskId))
            val container = AutoSecretaryApplication.from(this).container()
            container.executors.timerSerial.execute { container.flowWakeScheduler.reschedule() }
        }
        // Android must accept the result before a process death can make the receipt unreachable.
        finish()
        if (isFinishing) editor.discardSession()
    }

    private fun confirmDiscard() {
        android.app.AlertDialog.Builder(this).setMessage("Änderungen verwerfen?")
            .setNegativeButton("Weiter bearbeiten", null)
            .setPositiveButton("Verwerfen") { _, _ -> finishEditor() }.show()
    }

    companion object { const val TASK_ID = "flow_task_id" }
}
