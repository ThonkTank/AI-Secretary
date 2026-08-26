package de.thonktank.autosecretary.presentation.editor

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.LocalDensity
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.EditorUiState
import de.thonktank.autosecretary.TaskEditorValidator
import java.time.LocalDate
import java.time.LocalTime

/**
 * Java-friendly mount boundary for the stateless Compose editor.
 *
 * The latest values are render inputs only. Every mutation is sent to [Listener] and must return
 * through the authoritative TaskEditorViewModel before it becomes visible.
 */
class TaskEditorComposeHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AbstractComposeView(context, attrs) {
    interface Listener {
        fun onDraftChanged(draft: EditorUiState)
        fun onSave(draft: EditorUiState)
        fun onDelete(taskId: String)
        fun onDismiss()
    }

    private var editorState by mutableStateOf(EditorUiState.closed())
    private var palette by mutableStateOf(DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO))
    private var today by mutableStateOf(LocalDate.now())
    private var listener: Listener? = null
    private var topInsetPx by mutableStateOf(0)
    private var bottomInsetPx by mutableStateOf(0)

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    fun bind(
        state: EditorUiState,
        palette: DayPalette,
        today: LocalDate,
        listener: Listener,
    ) {
        this.listener = listener
        this.editorState = state
        this.palette = palette
        this.today = today
    }

    fun handleBack(): Boolean {
        if (!editorState.open) return false
        TaskEditorComposeDispatcher(
            editorState,
            today,
            callbacks(),
            TaskEditorValidator(),
        ).back()
        return true
    }

    fun setContentInsets(topPx: Int, bottomPx: Int) {
        topInsetPx = topPx.coerceAtLeast(0)
        bottomInsetPx = bottomPx.coerceAtLeast(0)
    }

    fun dispose() {
        listener = null
        disposeComposition()
    }

    @Composable
    override fun Content() {
        val density = LocalDensity.current
        TaskEditorComposeScreen(
            editorState,
            palette,
            today,
            callbacks(),
            contentTopInset = with(density) { topInsetPx.toDp() },
            contentBottomInset = with(density) { bottomInsetPx.toDp() },
        )
    }

    private fun callbacks() = TaskEditorComposeCallbacks(
        onDraftChanged = { listener?.onDraftChanged(it) },
        onSave = { listener?.onSave(it) },
        onDelete = { listener?.onDelete(it) },
        onDismiss = { listener?.onDismiss() },
    )
}
