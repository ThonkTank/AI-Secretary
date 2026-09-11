package de.thonktank.autosecretary.presentation.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.FlowEditorState
import de.thonktank.autosecretary.FlowEditorViewModel
import de.thonktank.autosecretary.domain.model.FlowTileGraph
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private data class TileDrag(
    val source: String, val mode: FlowTileMove, val origin: Offset,
    val pointer: Offset, val boxes: Map<String, Rect>, val keyboard: Boolean = false,
)

/** The resting surface is tiles only. Additional handles appear on long-press/keyboard request. */
@Composable
internal fun FlowTileBoard(state: FlowEditorState, palette: DayPalette, editor: FlowEditorViewModel,
                           scroll: ScrollState, viewport: Rect) {
    val graph = state.draft.graph
    val boxes = remember(graph) { mutableMapOf<String, Rect>() }
    val landingBoxes = remember(graph) { mutableMapOf<String, Rect>() }
    var boardOrigin by remember { mutableStateOf(Offset.Zero) }
    var drag by remember(graph) { mutableStateOf<TileDrag?>(null) }
    var proposal by remember(graph) { mutableStateOf<FlowTileProposal?>(null) }
    var selected by remember(graph) { mutableStateOf<String?>(null) }
    var mode by remember(graph) { mutableStateOf<FlowTileMove?>(null) }
    var target by remember(graph) { mutableStateOf<String?>(null) }
    val toolsIntoView = remember { BringIntoViewRequester() }
    val currentViewport by rememberUpdatedState(viewport)
    val density = LocalDensity.current
    val margin = with(density) { 24.dp.toPx() }
    val edge = with(density) { 56.dp.toPx() }
    val speed = with(density) { 420.dp.toPx() }
    fun cancel() { drag = null; proposal = null }
    fun closeTools() { cancel(); selected = null; mode = null; target = null }
    fun apply() {
        val value = proposal
        closeTools()
        value?.let {
            if (it.mode == FlowTileMove.JOIN) editor.join(listOf(it.source), it.target)
            else editor.place(it.source, it.target, requireNotNull(it.placement), it.mode == FlowTileMove.BRANCH)
        }
    }
    fun preview(value: TileDrag) {
        val point = value.pointer - boardOrigin
        val movingCenter = value.boxes.getValue(value.source).center + point - value.origin
        proposal = tileDropProposal(graph, value.source, value.mode,
            if (value.mode == FlowTileMove.JOIN) point else movingCenter, value.boxes, margin)
    }
    fun begin(id: String, kind: FlowTileMove, pointer: Offset, keyboard: Boolean = false, fromHandle: Boolean = false) {
        if (!boxes.containsKey(id)) return
        proposal = null
        drag = TileDrag(id, kind, if (fromHandle) boxes.getValue(id).center else pointer - boardOrigin,
            pointer, boxes.toMap(), keyboard)
    }
    fun move(pointer: Offset) { drag?.let { drag = it.copy(pointer = pointer); preview(requireNotNull(drag)) } }
    fun openTools(id: String) { cancel(); selected = id; mode = null; target = null }

    BackHandler(drag != null || selected != null) { closeTools() }
    LaunchedEffect(selected) { if (selected != null) toolsIntoView.bringIntoView() }
    // One frame loop per gesture, not one coroutine per pointer event. Scrolling never changes
    // the frozen hit boxes; only the pointer's offset into the content is adjusted.
    LaunchedEffect(drag?.source, drag?.keyboard) {
        if (drag == null || drag?.keyboard == true) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive && drag != null) {
            val now = withFrameNanos { it }
            val dt = ((now - previous) / 1_000_000_000f).coerceIn(0f, .05f)
            previous = now
            drag?.let { value ->
                val velocity = flowEdgeVelocity(value.pointer.y, currentViewport, edge, speed)
                if (velocity != 0f) scroll.scrollBy(velocity * dt)
                preview(value)
            }
        }
    }
    val currentDrag = drag
    val shown = proposal?.graph ?: graph
    val spans = tileSpans(shown)
    val movingIds = currentDrag?.let { if (it.mode == FlowTileMove.BRANCH) graph.branchFrom(it.source) else setOf(it.source) }.orEmpty()
    Column(Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false
        else if (event.key == Key.Escape && (drag != null || selected != null)) { closeTools(); true }
        else drag?.takeIf { it.keyboard }?.let { value ->
            when (event.key) {
                Key.Enter, Key.Spacebar -> { apply(); true }
                Key.DirectionLeft -> { move(value.pointer + Offset(-margin * 2, 0f)); true }
                Key.DirectionRight -> { move(value.pointer + Offset(margin * 2, 0f)); true }
                Key.DirectionUp -> { move(value.pointer + Offset(0f, -margin * 2)); true }
                Key.DirectionDown -> { move(value.pointer + Offset(0f, margin * 2)); true }
                else -> false
            }
        } ?: false
    }) {
        Layout(content = {
            state.draft.steps.forEach { step -> key(step.id) {
                val isRoot = graph.roots().contains(step.id)
                val cadence = "Start · alle ${step.intervalDays ?: 1} Tage"
                val highlighted = step.id == selected || step.id == proposal?.target
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .flowTileGrip(graph, { begin(step.id, FlowTileMove.STEP, it) }, ::move, ::apply, ::cancel)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) false
                        else when {
                            event.key == Key.Menu || event.key == Key.F10 && event.isShiftPressed -> { openTools(step.id); true }
                            event.key == Key.Spacebar && drag == null -> {
                                boxes[step.id]?.let { begin(step.id, FlowTileMove.STEP, it.center + boardOrigin, true) }; true
                            }
                            else -> false
                        }
                    }
                    .background(Color.argb(palette.leaf1), RoundedCornerShape(8.dp))
                    .border(if (highlighted) 2.dp else 1.dp,
                        Color.argb(if (highlighted) palette.accent else palette.leaf1Edge), RoundedCornerShape(8.dp))
                    .combinedClickable(role = Role.Button, onLongClickLabel = "Anordnung und Startrhythmus",
                        onLongClick = { openTools(step.id) }, onClick = {
                            if (mode != null && selected != step.id) { target = step.id; proposal = null }
                            else if (mode == null) editor.openStep(step.id)
                        })
                    .semantics {
                        contentDescription = step.text
                        stateDescription = if (isRoot) cadence else "Folgeschritt"
                        customActions = listOf(CustomAccessibilityAction("Anordnen") { openTools(step.id); true }) +
                            if (isRoot) listOf(CustomAccessibilityAction("Startrhythmus bearbeiten") { editor.openCadence(step.id); true }) else emptyList()
                    }.testTag("flow-editor:tile:${step.id}").padding(horizontal = 1.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center) {
                    BasicText(step.text, style = TextStyle(color = Color.argb(palette.ink),
                        fontFamily = EditorSans, fontSize = 16.sp, textAlign = TextAlign.Center,
                        localeList = LocaleList(Locale("de-DE")),
                        hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph))
                }
            } }
        }, modifier = Modifier.fillMaxWidth().testTag("flow-editor:board")
            .drawBehind {
                // The prospective landing tiles remain visible underneath the lifted tiles.
                // No lines or ports are needed to preview the new widths and prerequisites.
                if (currentDrag != null && proposal != null) movingIds.forEach { id ->
                    landingBoxes[id]?.let { box ->
                        drawRoundRect(Color.argb(palette.accent).copy(alpha = .1f), box.topLeft,
                            Size(box.width, box.height), CornerRadius(8.dp.toPx()))
                        drawRoundRect(Color.argb(palette.accent), box.topLeft,
                            Size(box.width, box.height), CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()))
                    }
                }
            }
            .onGloballyPositioned { boardOrigin = it.localToRoot(Offset.Zero) }) { measurables, constraints ->
            val gap = 6.dp.roundToPx()
            val width = constraints.maxWidth
            val places = measurables.mapIndexed { index, measurable ->
                val id = state.draft.steps[index].id
                val span = spans.getValue(id)
                val tileWidth = if (id in movingIds) currentDrag!!.boxes.getValue(id).width.roundToInt()
                    else ((span.right - span.left) * width).roundToInt() -
                        (if (span.left > 0f) gap / 2 else 0) - (if (span.right < 1f) gap / 2 else 0)
                measurable.measure(Constraints.fixedWidth(tileWidth.coerceAtLeast(1)))
            }
            val heights = mutableMapOf<Int, Int>()
            places.forEachIndexed { i, p -> val row = spans.getValue(state.draft.steps[i].id).row
                heights[row] = maxOf(heights[row] ?: 0, p.height) }
            val rowY = mutableMapOf<Int, Int>(); var total = 0
            heights.keys.sorted().forEach { row -> rowY[row] = total; total += heights.getValue(row) + gap }
            val delta = currentDrag?.let { it.pointer - boardOrigin - it.origin } ?: Offset.Zero
            layout(width, (total - gap).coerceAtLeast(0)) {
                places.forEachIndexed { i, p ->
                    val id = state.draft.steps[i].id; val span = spans.getValue(id)
                    val left = (span.left * width).roundToInt() + if (span.left > 0f) gap / 2 else 0
                    val top = rowY.getValue(span.row)
                    val landingWidth = ((span.right - span.left) * width).roundToInt() -
                        (if (span.left > 0f) gap / 2 else 0) - (if (span.right < 1f) gap / 2 else 0)
                    landingBoxes[id] = Rect(left.toFloat(), top.toFloat(), left + landingWidth.toFloat(), top + p.height.toFloat())
                    if (currentDrag == null) boxes[id] = Rect(left.toFloat(), top.toFloat(), left + p.width.toFloat(), top + p.height.toFloat())
                    if (id in movingIds) {
                        val origin = currentDrag!!.boxes.getValue(id)
                        p.place((origin.left + delta.x).roundToInt(), (origin.top + delta.y).roundToInt(), zIndex = 1f)
                    } else p.place(left, top)
                }
            }
        }
        if (proposal != null || drag != null) {
            val value = proposal
            val hint = if (value == null) "Zum Ziel ziehen · Zurück bricht ab" else {
                val relation = if (value.mode == FlowTileMove.JOIN) "Zusammenführen mit"
                    else when (value.placement) {
                        FlowTileGraph.Placement.BEFORE -> "Vor"
                        FlowTileGraph.Placement.AFTER -> "Nach"
                        else -> "Neben"
                    }
                "$relation ${state.draft.step(value.target).text}"
            }
            EditorText(hint, Color.argb(palette.accent), 16, serif = false,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.padding(top = 8.dp))
        }
        selected?.let { source ->
            Column(Modifier.fillMaxWidth().padding(top = 12.dp).bringIntoViewRequester(toolsIntoView)
                .testTag("flow-editor:arrange")) {
                EditorText(state.draft.step(source).text, Color.argb(palette.ink), 22)
                val handles = listOf(FlowTileMove.STEP to "Schritt verschieben") +
                    (if (graph.branchFrom(source).size > 1) listOf(FlowTileMove.BRANCH to "Zweig verschieben") else emptyList()) +
                    listOf(FlowTileMove.JOIN to "Zusammenführen")
                handles.forEach { (kind, title) ->
                    EditorButton(title, palette, { mode = kind; target = null; proposal = null },
                        modifier = Modifier.fillMaxWidth().testTag("flow-editor:handle:${kind.name}")
                            .flowTileGrip(graph, { begin(source, kind, it, fromHandle = true) }, ::move, ::apply, ::cancel))
                }
                if (graph.roots().contains(source) && mode == null)
                    EditorButton("Start · alle ${state.draft.step(source).intervalDays ?: 1} Tage", palette, { editor.openCadence(source) })
                mode?.let { kind ->
                    // The same proposals support a complete non-drag path, including switch access.
                    graph.stepIds.filter { it != source }.forEach { id ->
                        val choices = if (kind == FlowTileMove.JOIN) listOfNotNull(tileProposal(graph, source, id, kind))
                            else FlowTileGraph.Placement.values().mapNotNull { tileProposal(graph, source, id, kind, it) }
                        if (choices.isNotEmpty()) {
                            EditorButton(state.draft.step(id).text, palette, { target = id; proposal = null },
                                modifier = Modifier.fillMaxWidth().testTag("flow-editor:target:$id"))
                            if (target == id) choices.forEach { option ->
                                val label = if (kind == FlowTileMove.JOIN) "Gemeinsamer Folgeschritt"
                                    else when (option.placement) {
                                        FlowTileGraph.Placement.BEFORE -> "Davor"
                                        FlowTileGraph.Placement.AFTER -> "Dahinter"
                                        else -> "Daneben"
                                    }
                                EditorButton(label, palette, { proposal = option }, modifier = Modifier.fillMaxWidth()
                                    .testTag("flow-editor:placement:$id:${option.placement?.name ?: "JOIN"}"))
                            }
                        }
                    }
                    if (proposal != null && drag == null) EditorButton("Übernehmen", palette, ::apply, primary = true,
                        modifier = Modifier.testTag("flow-editor:apply-move"))
                }
                EditorButton("Abbrechen", palette, ::closeTools, modifier = Modifier.testTag("flow-editor:cancel-move"))
            }
        }
    }
}
