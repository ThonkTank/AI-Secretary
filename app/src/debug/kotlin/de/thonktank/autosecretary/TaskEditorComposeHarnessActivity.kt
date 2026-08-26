package de.thonktank.autosecretary

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import de.thonktank.autosecretary.presentation.editor.TaskEditorComposeHostView
import java.time.LocalDate
import java.time.LocalTime

/** Debug-only host used for Compose side-by-side, semantics and interaction verification. */
class TaskEditorComposeHarnessActivity : ComponentActivity(), TaskEditorComposeHostView.Listener {
    private companion object { const val STATE_EDITOR = "compose_editor_state" }
    lateinit var editor: TaskEditorComposeHostView
        private set
    var state: EditorUiState = EditorUiState.create()
        private set
    var palette: DayPalette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT)
        private set
    var today: LocalDate = LocalDate.of(2026, 8, 23)
        private set
    var saveCount: Int = 0
        private set
    var deleteCount: Int = 0
        private set
    var dismissCount: Int = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getBundle(STATE_EDITOR)?.let { restored ->
            state = EditorUiState.fromBundle(restored)
        }
        val root = FrameLayout(this)
        val forest = ForestBackdropView(this).also { it.setPalette(palette) }
        root.addView(forest, FrameLayout.LayoutParams(-1, -1))
        editor = TaskEditorComposeHostView(this).also { it.id = R.id.task_editor_compose_host }
        root.addView(editor, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (editor.handleBack()) return
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
        bind()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle(STATE_EDITOR, state.toBundle())
        super.onSaveInstanceState(outState)
    }

    fun render(value: EditorUiState, palette: DayPalette = this.palette, today: LocalDate = this.today) {
        state = value
        this.palette = palette
        this.today = today
        bind()
    }

    override fun onDraftChanged(draft: EditorUiState) {
        state = draft
        bind()
    }

    override fun onSave(draft: EditorUiState) {
        saveCount++
        state = de.thonktank.autosecretary.editor.TaskEditorStateReducer.saving(draft, true)
        bind()
    }

    override fun onDelete(taskId: String) {
        deleteCount++
        state = de.thonktank.autosecretary.editor.TaskEditorStateReducer.saving(state, true)
        bind()
    }

    override fun onDismiss() {
        dismissCount++
        state = EditorUiState.closed()
        bind()
    }

    private fun bind() {
        editor.bind(state, palette, today, this)
    }
}
