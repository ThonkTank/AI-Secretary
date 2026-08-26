package de.thonktank.autosecretary.presentation.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.EditorUiState
import de.thonktank.autosecretary.R
import de.thonktank.autosecretary.TaskEditorValidator
import de.thonktank.autosecretary.ValidationIssue
import de.thonktank.autosecretary.editor.TaskEditorStateReducer
import de.thonktank.autosecretary.presentation.TaskEditorTextFormatter
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider
import java.time.LocalDate

@Immutable
data class TaskEditorComposeCallbacks(
    val onDraftChanged: (EditorUiState) -> Unit,
    val onSave: (EditorUiState) -> Unit,
    val onDelete: (String) -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
fun TaskEditorComposeScreen(
    state: EditorUiState,
    palette: DayPalette,
    today: LocalDate,
    callbacks: TaskEditorComposeCallbacks,
    modifier: Modifier = Modifier,
    contentTopInset: Dp = 0.dp,
    contentBottomInset: Dp = 0.dp,
) {
    if (!state.open || state.loading) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val windowWidthDp = with(density) { windowInfo.containerSize.width.toDp().value.toInt() }
    val layout = remember(windowWidthDp, density.fontScale) {
        EditorLayout.from(windowWidthDp, density.fontScale)
    }
    val validator = remember { TaskEditorValidator() }
    val formatter = remember(context) {
        TaskEditorTextFormatter(AndroidUiTextProvider(context))
    }
    val dispatcher = remember(state, today, callbacks, validator) {
        TaskEditorComposeDispatcher(state, today, callbacks, validator)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .testTag("task-editor:compose"),
    ) {
        val hiddenByPrompt = if (state.prompt == EditorUiState.Prompt.NONE) Modifier
        else Modifier.clearAndSetSemantics { }
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = contentTopInset, bottom = contentBottomInset)
                .then(hiddenByPrompt),
        ) {
            EditorHeader(state, palette, layout, dispatcher)
            EditorPageViewport(
                state = state,
                palette = palette,
                today = today,
                layout = layout,
                formatter = formatter,
                dispatcher = dispatcher,
                modifier = Modifier.weight(1f),
            )
            EditorFooter(state, palette, layout, dispatcher)
        }
        if (state.prompt != EditorUiState.Prompt.NONE) {
            EditorPrompt(state, palette, layout, dispatcher)
        }
    }
}

@Composable
private fun EditorHeader(
    state: EditorUiState,
    palette: DayPalette,
    layout: EditorLayout,
    dispatcher: TaskEditorComposeDispatcher,
) {
    val detail = state.expandedStepId != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(start = layout.pageStart, end = layout.pageEnd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorButton(
            text = stringResource(if (detail) R.string.editor_back_steps else R.string.editor_cancel),
            palette = palette,
            onClick = { if (detail) dispatcher.closeStep() else dispatcher.requestClose() },
            contentDescription = stringResource(if (detail) R.string.editor_back_steps else R.string.editor_cancel),
        )
        Spacer(Modifier.weight(1f))
        EditorText(
            text = if (detail) {
                stringResource(R.string.step_marker, dispatcher.expandedIndex() + 1)
            } else {
                stringResource(if (state.taskId == null) R.string.editor_ctx_new else R.string.editor_ctx_edit)
            },
            color = Color.argb(palette.muted),
            size = 18,
            italic = true,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun EditorPageViewport(
    state: EditorUiState,
    palette: DayPalette,
    today: LocalDate,
    layout: EditorLayout,
    formatter: TaskEditorTextFormatter,
    dispatcher: TaskEditorComposeDispatcher,
    modifier: Modifier,
) {
    val pageKey = state.page.name + ':' + (state.expandedStepId ?: "list")
    val direction = if (state.returnToSummary) -1 else 1
    AnimatedContent(
        targetState = pageKey,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            val duration = palette.motion.stateChangeDurationMs.toInt()
            val easing = CubicBezierEasing(.2f, .7f, .3f, 1f)
            val enter: EnterTransition = fadeIn(tween(duration, easing = easing)) +
                    slideInHorizontally(tween(duration, easing = easing)) { direction * it / 10 }
            val exit: ExitTransition = fadeOut(tween(duration, easing = easing)) +
                    slideOutHorizontally(tween(duration, easing = easing)) { -direction * it / 12 }
            enter togetherWith exit
        },
        label = "task-editor-page",
    ) { animatedPageKey ->
        val animatedPage = EditorUiState.Page.valueOf(animatedPageKey.substringBefore(':'))
        val animatedStepId = animatedPageKey.substringAfter(':').takeUnless { it == "list" }
        val displayedState = state
            .withPage(animatedPage, state.returnToSummary)
            .withExpandedStep(animatedStepId)
        var restoredScroll by rememberSaveable(animatedPageKey) { mutableIntStateOf(0) }
        val scroll = rememberScrollState(restoredScroll)
        LaunchedEffect(scroll) {
            snapshotFlow { scroll.value }.collect { restoredScroll = it }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .testTag("task-editor:scroll:$animatedPageKey")
                .padding(
                    start = layout.pageStart,
                    top = 8.dp,
                    end = layout.pageEnd,
                    bottom = 18.dp,
                ),
        ) {
            val summary = displayedState.page == EditorUiState.Page.SUMMARY &&
                    displayedState.expandedStepId == null
            val pageModifier = if (summary) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            }
            if (summary) {
                EditorPageContent(
                    displayedState,
                    palette,
                    today,
                    layout,
                    formatter,
                    dispatcher,
                    pageModifier,
                )
            } else {
                LeafSurface(
                    palette = palette,
                    modifier = pageModifier,
                    rotation = -.7f,
                    padding = androidx.compose.foundation.layout.PaddingValues(
                        start = layout.leafHorizontal,
                        top = layout.leafTop,
                        end = layout.leafHorizontal,
                        bottom = layout.leafBottom,
                    ),
                ) {
                    EditorPageContent(
                        displayedState,
                        palette,
                        today,
                        layout,
                        formatter,
                        dispatcher,
                        Modifier.fillMaxWidth(),
                    )
                }
            }
            if (state.storageError.isNotEmpty()) {
                EditorText(
                    state.storageError,
                    Color.argb(palette.bad),
                    14,
                    Modifier.padding(top = 12.dp),
                    serif = false,
                    bold = true,
                )
            }
        }
    }
}

@Composable
private fun EditorFooter(
    state: EditorUiState,
    palette: DayPalette,
    layout: EditorLayout,
    dispatcher: TaskEditorComposeDispatcher,
) {
    val detail = state.expandedStepId != null
    val primaryText = stringResource(
        when {
            state.saving -> R.string.update_busy
            detail -> R.string.step_apply
            state.page == EditorUiState.Page.SUMMARY -> R.string.action_save
            else -> R.string.editor_next
        },
    )
    val primary: @Composable () -> Unit = {
        EditorButton(
            text = primaryText,
            palette = palette,
            onClick = {
                when {
                    detail -> dispatcher.applyStepDetail()
                    state.page == EditorUiState.Page.SUMMARY -> dispatcher.save()
                    else -> dispatcher.advance()
                }
            },
            enabled = !state.saving && !TaskEditorStateReducer.hasVisibleBlockingIssue(state, detail),
            primary = true,
            modifier = Modifier.height(52.dp),
        )
    }
    val secondary: @Composable () -> Unit = {
        if (!detail && state.page != EditorUiState.Page.TITLE) {
            EditorButton(
                text = stringResource(
                    if (state.page == EditorUiState.Page.SUMMARY) R.string.action_discard
                    else R.string.editor_back,
                ),
                palette = palette,
                onClick = {
                    if (state.page == EditorUiState.Page.SUMMARY) dispatcher.requestClose()
                    else dispatcher.previous()
                },
            )
        }
    }
    val destructive: @Composable () -> Unit = {
        when {
            detail -> EditorButton(
                text = stringResource(R.string.step_remove),
                palette = palette,
                onClick = dispatcher::removeExpandedStep,
                destructive = true,
            )
            state.page == EditorUiState.Page.SUMMARY && state.taskId != null -> EditorButton(
                text = stringResource(R.string.action_delete),
                palette = palette,
                onClick = dispatcher::showDeletePrompt,
                destructive = true,
            )
        }
    }
    if (layout.compact) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(layout.footerHeight)
                .padding(horizontal = layout.pageStart, vertical = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                primary()
                Spacer(Modifier.width(18.dp))
                secondary()
            }
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destructive()
                Spacer(Modifier.weight(1f))
                EditorProgress(state, palette, detail)
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(layout.footerHeight)
                .padding(start = layout.pageStart, end = layout.pageEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            primary()
            Spacer(Modifier.width(18.dp))
            secondary()
            Spacer(Modifier.weight(1f))
            destructive()
            EditorProgress(state, palette, detail)
        }
    }
}

@Composable
private fun EditorProgress(state: EditorUiState, palette: DayPalette, detail: Boolean) {
    if (detail || state.page == EditorUiState.Page.SUMMARY) return
    val pages = EditorUiState.Page.entries
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        pages.forEach { page ->
            Box(
                Modifier
                    .width(30.dp)
                    .height(4.dp)
                    .background(
                        if (page.ordinal <= state.page.ordinal) Color.argb(palette.light)
                        else Color.argb(palette.dot).copy(alpha = .5f),
                        androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

@Composable
private fun EditorPrompt(
    state: EditorUiState,
    palette: DayPalette,
    layout: EditorLayout,
    dispatcher: TaskEditorComposeDispatcher,
) {
    val deleting = state.prompt == EditorUiState.Prompt.DELETE
    val promptPaneTitle = stringResource(
        if (deleting) R.string.ask_delete_kicker else R.string.ask_discard_kicker,
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.argb(palette.ink).copy(alpha = .53f))
            .padding(start = layout.pageStart, end = layout.pageEnd),
        contentAlignment = Alignment.TopCenter,
    ) {
        LeafSurface(
            palette = palette,
            modifier = Modifier
                .padding(top = if (layout.compact) 150.dp else 250.dp)
                .fillMaxWidth()
                .testTag("task-editor:prompt")
                .semantics { paneTitle = promptPaneTitle },
            padding = androidx.compose.foundation.layout.PaddingValues(28.dp, 26.dp),
        ) {
            Column {
                    EditorText(
                        stringResource(if (deleting) R.string.ask_delete_kicker else R.string.ask_discard_kicker),
                        Color.argb(if (deleting) palette.bad else palette.accent),
                        19,
                        italic = true,
                    )
                    EditorText(
                        if (deleting) stringResource(R.string.ask_delete_title, state.title)
                        else stringResource(R.string.ask_discard_title),
                        Color.argb(palette.ink),
                        29,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    val body = if (deleting) {
                        if (state.stepStates.isEmpty()) stringResource(R.string.ask_delete_body)
                        else stringResource(R.string.ask_delete_body_steps, state.stepStates.size)
                    } else {
                        if (state.stepStates.isEmpty()) stringResource(R.string.ask_discard_body)
                        else stringResource(R.string.ask_discard_body_steps, state.stepStates.size)
                    }
                    EditorText(
                        body,
                        Color.argb(palette.ink2),
                        16,
                        Modifier.padding(top = 8.dp),
                        serif = false,
                    )
                    Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        EditorButton(
                            text = stringResource(
                                if (state.saving) R.string.update_busy
                                else if (deleting) R.string.ask_delete_confirm
                                else R.string.ask_discard_confirm,
                            ),
                            palette = palette,
                            onClick = {
                                if (deleting) dispatcher.delete() else dispatcher.dismiss()
                            },
                            primary = true,
                            destructive = deleting,
                            enabled = !state.saving,
                            modifier = Modifier.height(52.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        EditorButton(
                            text = stringResource(if (deleting) R.string.ask_delete_keep else R.string.ask_discard_keep),
                            palette = palette,
                            onClick = dispatcher::closePrompt,
                        )
                    }
            }
        }
    }
}

internal class TaskEditorComposeDispatcher(
    val state: EditorUiState,
    private val today: LocalDate,
    private val callbacks: TaskEditorComposeCallbacks,
    private val validator: TaskEditorValidator,
) {
    fun emit(next: EditorUiState) {
        val visible = TaskEditorStateReducer.liveValidation(next, validator.issues(next, today))
        callbacks.onDraftChanged(visible)
    }

    fun advance() = emit(TaskEditorStateReducer.advance(state, validator.issues(state, today)))

    fun applyStepDetail() = emit(
        TaskEditorStateReducer.applyStepDetail(state, validator.issues(state, today)),
    )

    fun save() {
        val issues = validator.issues(state, today)
        if (issues.isEmpty()) callbacks.onSave(state)
        else emit(TaskEditorStateReducer.routeValidationFailure(state, issues))
    }

    fun requestClose() {
        if (state.dirty) showPrompt(EditorUiState.Prompt.DISCARD) else callbacks.onDismiss()
    }

    fun back() {
        when {
            state.prompt != EditorUiState.Prompt.NONE -> closePrompt()
            state.expandedStepId != null -> closeStep()
            state.returnToSummary -> navigate(EditorUiState.Page.SUMMARY, false)
            state.page == EditorUiState.Page.TITLE -> requestClose()
            state.page == EditorUiState.Page.SUMMARY && state.taskId != null -> requestClose()
            else -> previous()
        }
    }

    fun previous() {
        val target = when (state.page) {
            EditorUiState.Page.TITLE -> EditorUiState.Page.TITLE
            EditorUiState.Page.SCHEDULE -> EditorUiState.Page.TITLE
            EditorUiState.Page.STEPS -> EditorUiState.Page.SCHEDULE
            EditorUiState.Page.SUMMARY -> EditorUiState.Page.STEPS
        }
        navigate(if (state.returnToSummary) EditorUiState.Page.SUMMARY else target, false)
    }

    fun navigate(page: EditorUiState.Page, returnToSummary: Boolean) =
        emit(TaskEditorStateReducer.navigate(state, page, returnToSummary))

    fun closeStep() = emit(TaskEditorStateReducer.expandStep(state, null))

    fun removeExpandedStep() {
        val index = expandedIndex()
        if (index >= 0) emit(TaskEditorStateReducer.removeStep(state, index))
    }

    fun expandedIndex(): Int = state.stepStates.indexOfFirst { it.id == state.expandedStepId }

    fun showDeletePrompt() = showPrompt(EditorUiState.Prompt.DELETE)

    fun showPrompt(prompt: EditorUiState.Prompt) {
        if (prompt == EditorUiState.Prompt.DELETE && state.taskId == null) return
        emit(TaskEditorStateReducer.feedback(state, state.issues, prompt, state.storageError))
    }

    fun closePrompt() = showPrompt(EditorUiState.Prompt.NONE)
    fun delete() = state.taskId?.let(callbacks.onDelete)
    fun dismiss() = callbacks.onDismiss()
}
