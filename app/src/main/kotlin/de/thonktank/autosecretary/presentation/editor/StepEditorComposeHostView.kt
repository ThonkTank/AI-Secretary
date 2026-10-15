package de.thonktank.autosecretary.presentation.editor

import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.R
import de.thonktank.autosecretary.StepEditorValidator
import de.thonktank.autosecretary.presentation.today.StepEditDraft
import de.thonktank.autosecretary.presentation.today.TodayViewModel

/** Mount boundary for a single-step modal, independent of the task wizard. */
class StepEditorComposeHostView(context: Context) : AbstractComposeView(context) {
    private var draft by mutableStateOf<StepEditDraft?>(null)
    private var palette by mutableStateOf(DayPalette.at(java.time.LocalTime.NOON, DayPalette.Mode.AUTO))
    private var model: TodayViewModel? = null

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    fun bind(draft: StepEditDraft?, palette: DayPalette, model: TodayViewModel) {
        this.model = model
        this.palette = palette
        this.draft = draft
    }

    @Composable
    override fun Content() {
        val value = draft ?: return
        StepEditorDialog(value, palette,
            onChange = { model?.changeStepEdit(it) },
            onSave = { model?.saveStepEdit() },
            onDismiss = { model?.cancelStepEdit() })
    }
}

@Composable
internal fun StepEditorDialog(
    draft: StepEditDraft,
    palette: DayPalette,
    onChange: (de.thonktank.autosecretary.EditorStepState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!draft.saving) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !draft.saving,
            dismissOnClickOutside = !draft.saving,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            // API 26 needs an explicit initial IME mode; resize also keeps touch
            // scroll coordinates above the keyboard instead of behind its window.
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            onDispose { }
        }
        val focus = rememberSaveable(draft.id) { mutableStateOf<String?>(null) }
        val issues = if (draft.validated) StepEditorValidator.issues(draft.step, draft.showCadence()) else emptySet()
        CompositionLocalProvider(LocalEditorFocusedInputTag provides focus) {
            BoxWithConstraints(Modifier.fillMaxWidth(.94f).imePadding()) {
                val layout = EditorLayout.from(maxWidth.value.toInt(), LocalDensity.current.fontScale)
                Column(Modifier.fillMaxWidth().heightIn(max = maxHeight * .92f)
                    .background(Color.argb(palette.leaf1), RoundedCornerShape(24.dp))
                    .padding(20.dp).testTag("step-editor:dialog").semantics { testTagsAsResourceId = true }) {
                    EditorText(stringResource(R.string.action_edit_step), Color.argb(palette.ink), 24)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                        .testTag("step-editor:fields")) {
                        if (draft.prescriptionProtected) {
                            EditorText(stringResource(R.string.step_edit_deferred), Color.argb(palette.muted), 14,
                                Modifier.padding(top = 8.dp))
                        }
                        EditorStepFields(draft.step, draft.showCadence(), palette, layout, issues,
                            requestInitialFocus = true, multilineNote = true, showQuestion = false) { if (!draft.saving) onChange(it) }
                        EditorText(stringResource(R.string.step_edit_future_hint), Color.argb(palette.muted), 14,
                            Modifier.padding(top = 16.dp))
                    }
                    if (draft.error.isNotEmpty()) {
                        EditorText(draft.error, Color.argb(palette.bad), 16,
                            Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                        EditorButton(stringResource(android.R.string.cancel), palette, onDismiss,
                            enabled = !draft.saving, modifier = Modifier.testTag("step-editor:cancel"))
                        EditorButton(stringResource(R.string.action_save), palette, onSave,
                            enabled = !draft.saving, primary = true, modifier = Modifier.testTag("step-editor:save"))
                    }
                }
            }
        }
    }
}
