package de.thonktank.autosecretary.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.FlowEditorForm
import de.thonktank.autosecretary.FlowEditorState
import de.thonktank.autosecretary.FlowEditorViewModel
import de.thonktank.autosecretary.domain.model.FlowTileGraph
import kotlin.math.abs
import kotlin.math.roundToInt

/** Dedicated flow surface; normal task-editor pages are deliberately not reused or changed. */
@Composable
fun FlowTileEditorScreen(
    state: FlowEditorState,
    palette: DayPalette,
    editor: FlowEditorViewModel,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusedInput = rememberSaveable { mutableStateOf<String?>(null) }
    CompositionLocalProvider(LocalEditorFocusedInputTag provides focusedInput) {
        FlowTileEditorContent(state, palette, editor, onSave, onCancel, modifier)
    }
}

@Composable
private fun FlowTileEditorContent(state: FlowEditorState, palette: DayPalette,
    editor: FlowEditorViewModel, onSave: () -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().background(Color.argb(palette.background))
        .verticalScroll(rememberScrollState()).padding(16.dp).testTag("flow-editor")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            EditorButton("Abbrechen", palette, onCancel)
            EditorText("${state.page}/2", Color.argb(palette.muted), 16, serif = false,
                modifier = Modifier.padding(top = 14.dp))
            EditorButton("↶", palette, editor::undo, enabled = state.canUndo, contentDescription = "Rückgängig")
        }
        state.error?.let { EditorText(it, Color.argb(palette.bad), 16, serif = false,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        when {
            state.form != null -> FlowEditorFormSurface(state.form, state, palette, editor)
            state.page == 1 -> {
                EditorInput(state.draft.name, editor::rename, palette, hint = "Ablaufname",
                    textSize = 30, serif = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Ablaufname" })
                Spacer(Modifier.height(16.dp))
                FlowTiles(state, palette, editor)
                EditorButton("+ Schritt", palette, { editor.openStep(null) }, modifier = Modifier.fillMaxWidth())
                EditorButton("Weiter", palette, editor::next, primary = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
            }
            else -> {
                EditorText("Kapazitäten", Color.argb(palette.ink), 30)
                state.draft.capacities.resources.forEach { resource ->
                    LeafSurface(palette, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Column {
                            EditorButton("${resource.name} · ${resource.capacity}", palette, { editor.openResource(resource.key) })
                            state.draft.capacities.leases.filter { it.resourceKey == resource.key }.forEach { lease ->
                                EditorButton("${lease.units} × ${state.draft.step(lease.acquireStepId).text} – ${state.draft.step(lease.releaseStepId).text}",
                                    palette, { editor.openLease(lease.key) })
                            }
                        }
                    }
                }
                EditorButton("+ Kapazität", palette, { editor.openResource(null) })
                EditorButton("+ Zuordnung", palette, { editor.openLease() },
                    enabled = state.draft.capacities.resources.isNotEmpty())
                EditorButton("Zurück", palette, editor::back)
                EditorButton("Fertig", palette, { if (editor.prepareSave()) onSave() },
                    primary = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun FlowEditorFormSurface(form: FlowEditorForm, state: FlowEditorState, palette: DayPalette,
                                  editor: FlowEditorViewModel) {
    val title = when (form.kind) { "step" -> if (form.id == null) "Neuer Schritt" else "Schritt bearbeiten"
        "resource" -> "Kapazität"; "lease" -> "Zuordnung"; else -> "Startrhythmus" }
    LeafSurface(palette, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("flow-editor:form")) {
        Column(Modifier.fillMaxWidth()) {
            EditorText(title, Color.argb(palette.ink), 28)
            when (form.kind) {
                "step" -> {
                    FlowField("Name", "name", form, palette, editor)
                    EditorText("Wartezeit danach", Color.argb(palette.ink2), 17,
                        modifier = Modifier.padding(top = 14.dp), serif = false)
                    val large = LocalDensity.current.fontScale >= 1.3f
                    if (large) {
                        FlowWaitAmount(form, palette, editor, Modifier.fillMaxWidth())
                        FlowWaitUnit(form, palette, editor, Modifier.fillMaxWidth())
                    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowWaitAmount(form, palette, editor, Modifier.weight(1f))
                        FlowWaitUnit(form, palette, editor, Modifier.weight(1f))
                    }
                    FlowCheck("Beim Start nachfragen", "ask", form, palette, editor)
                }
                "resource" -> {
                    FlowField("Name", "name", form, palette, editor)
                    FlowField("Gesamtmenge", "total", form, palette, editor, true)
                }
                "lease" -> {
                    FlowChoice("Kapazität", "resource", state.draft.capacities.resources.map { it.key to it.name }, form, palette, editor)
                    FlowField("Benötigte Menge", "quantity", form, palette, editor, true)
                    val steps = state.draft.steps.map { it.id to it.text }
                    FlowChoice("Reservieren bei", "from", steps, form, palette, editor)
                    FlowChoice("Freigeben nach", "to", steps, form, palette, editor)
                    FlowCheck("Nach der Wartezeit freigeben", "afterWait", form, palette, editor)
                }
                "cadence" -> FlowField("Alle … Tage", "days", form, palette, editor, true)
            }
            Spacer(Modifier.height(12.dp))
            if (LocalDensity.current.fontScale >= 1.3f) {
                EditorButton("Abbrechen", palette, editor::closeForm, modifier = Modifier.fillMaxWidth())
                EditorButton("Speichern", palette, editor::submitForm, primary = true, modifier = Modifier.fillMaxWidth())
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                EditorButton("Abbrechen", palette, editor::closeForm)
                EditorButton("Speichern", palette, editor::submitForm, primary = true)
            }
        }
    }
}

@Composable private fun FlowWaitAmount(form: FlowEditorForm, palette: DayPalette, editor: FlowEditorViewModel, modifier: Modifier) {
    EditorInput(form.fields["duration"].orEmpty(), { editor.field("duration", it) }, palette,
        number = true, modifier = modifier.semantics { contentDescription = "Wartezeit danach" }, tag = "flow-editor:duration")
}

@Composable private fun FlowWaitUnit(form: FlowEditorForm, palette: DayPalette, editor: FlowEditorViewModel, modifier: Modifier) {
    FlowChoice("Einheit", "unit", listOf("60000" to "Minuten", "3600000" to "Stunden", "86400000" to "Tage"),
        form, palette, editor, modifier)
}

@Composable private fun FlowField(label: String, field: String, form: FlowEditorForm, palette: DayPalette,
                                  editor: FlowEditorViewModel, number: Boolean = false) {
    EditorText(label, Color.argb(palette.ink2), 17, modifier = Modifier.padding(top = 14.dp), serif = false)
    EditorInput(form.fields[field].orEmpty(), { editor.field(field, it) }, palette, number = number,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }, tag = "flow-editor:$field")
}

@Composable private fun FlowCheck(label: String, field: String, form: FlowEditorForm, palette: DayPalette,
                                  editor: FlowEditorViewModel) {
    val checked = form.fields[field] == "true"
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox,
        onValueChange = { editor.field(field, it.toString()) }).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        EditorText(if (checked) "☑" else "☐", Color.argb(palette.accent), 20,
            modifier = Modifier.clearAndSetSemantics { }, serif = false)
        EditorText(label, Color.argb(palette.ink2), 17, serif = false)
    }
}

@Composable private fun FlowChoice(label: String, field: String, options: List<Pair<String, String>>,
                                   form: FlowEditorForm, palette: DayPalette, editor: FlowEditorViewModel,
                                   modifier: Modifier = Modifier) {
    var expanded by remember(form.kind, form.id, field) { mutableStateOf(false) }
    Column(modifier) {
        EditorButton(options.find { it.first == form.fields[field] }?.second ?: label, palette,
            { expanded = !expanded }, contentDescription = label,
            modifier = Modifier.fillMaxWidth().testTag("flow-editor:$field"))
        if (expanded) options.forEach { (value, text) ->
            EditorButton(text, palette, { editor.field(field, value); expanded = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}

internal data class FlowTileSpan(val row: Int, val left: Float, val right: Float)
internal fun tileSpans(graph: FlowTileGraph): Map<String, FlowTileSpan> {
    val result = linkedMapOf<String, FlowTileSpan>()
    val roots = graph.roots()
    graph.topologicalOrder().forEach { id ->
        val parents = graph.predecessors(id)
        result[id] = if (parents.isEmpty()) {
            val index = roots.indexOf(id)
            FlowTileSpan(0, index.toFloat() / roots.size, (index + 1f) / roots.size)
        } else if (parents.size == 1) {
            val parent = result.getValue(parents.single())
            val siblings = graph.successors(parents.single())
            val index = siblings.indexOf(id)
            val width = (parent.right - parent.left) / siblings.size
            FlowTileSpan(parent.row + 1, parent.left + index * width, parent.left + (index + 1) * width)
        } else FlowTileSpan(parents.maxOf { result.getValue(it).row } + 1,
            parents.minOf { result.getValue(it).left }, parents.maxOf { result.getValue(it).right })
    }
    return result
}

@Composable private fun FlowTiles(state: FlowEditorState, palette: DayPalette, editor: FlowEditorViewModel) {
    val graph = state.draft.graph
    val boxes = remember { mutableMapOf<String, Rect>() }
    var preview by remember(graph) { mutableStateOf<FlowTileGraph?>(null) }
    val spans = tileSpans(preview ?: graph)
    Layout(content = {
        state.draft.steps.forEach { step -> key(step.id) {
            val actions = graph.stepIds.filter { it != step.id }.flatMap { target ->
                val title = state.draft.step(target).text
                listOf(FlowTileGraph.Placement.BEFORE to "Vor", FlowTileGraph.Placement.AFTER to "Nach",
                    FlowTileGraph.Placement.BESIDE to "Neben").mapNotNull { (placement, label) ->
                    runCatching { graph.place(step.id, target, placement, false) }.getOrNull()?.let {
                        CustomAccessibilityAction("$label $title verschieben") { editor.place(step.id, target, placement); true }
                    }
                } + listOfNotNull(runCatching { graph.join(listOf(step.id), target) }.getOrNull()?.let {
                    CustomAccessibilityAction("Gemeinsamer Folgeschritt: $title") { editor.join(listOf(step.id), target); true }
                })
            } + if (graph.roots().contains(step.id)) listOf(CustomAccessibilityAction("Startrhythmus bearbeiten") {
                editor.openCadence(step.id); true
            }) else emptyList()
            Box(Modifier.onGloballyPositioned { boxes[step.id] = it.boundsInParent() }
                .pointerInput(graph, step.id) {
                    var point = Offset.Zero
                    var targets = emptyMap<String, Rect>()
                    var destination: Pair<String, FlowTileGraph.Placement>? = null
                    detectDragGestures(onDragStart = { local ->
                        targets = boxes.toMap(); point = (targets[step.id]?.topLeft ?: Offset.Zero) + local
                    }, onDragCancel = { preview = null; destination = null }, onDragEnd = {
                        destination?.let { (target, placement) -> editor.place(step.id, target, placement) }
                        preview = null; destination = null
                    }, onDrag = { change, delta ->
                        change.consume(); point += delta
                        val target = targets.filterKeys { it != step.id }.minByOrNull { (_, rect) -> (point - rect.center).getDistance() }
                        destination = target?.let { (id, rect) ->
                            val side = abs(point.x - rect.center.x) > rect.width * .3f
                            val placement = if (side) FlowTileGraph.Placement.BESIDE
                                else if (point.y < rect.center.y) FlowTileGraph.Placement.BEFORE else FlowTileGraph.Placement.AFTER
                            val proposed = runCatching { graph.place(step.id, id, placement, false) }.getOrNull()
                            preview = proposed
                            if (proposed == null) null else id to placement
                        }
                    })
                }.semantics { customActions = actions }.testTag("flow-editor:tile:${step.id}")) {
                LeafSurface(palette, clickableLabel = step.text, onClick = { editor.openStep(step.id) },
                    topEnd = 20, bottomStart = 20,
                    padding = PaddingValues(8.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    EditorText(step.text, Color.argb(palette.ink), 16, serif = false)
                }
            }
        } }
    }, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = 6.dp.roundToPx()
        val width = constraints.maxWidth
        val placeables = measurables.mapIndexed { index, measurable ->
            val span = spans.getValue(state.draft.steps[index].id)
            val tileWidth = ((span.right - span.left) * width).roundToInt() - gap
            measurable.measure(Constraints.fixedWidth(tileWidth.coerceAtLeast(1)))
        }
        val rowHeights = mutableMapOf<Int, Int>()
        placeables.forEachIndexed { index, p ->
            val row = spans.getValue(state.draft.steps[index].id).row
            rowHeights[row] = maxOf(rowHeights[row] ?: 0, p.height)
        }
        val rowY = mutableMapOf<Int, Int>(); var total = 0
        rowHeights.keys.sorted().forEach { row -> rowY[row] = total; total += rowHeights.getValue(row) + gap }
        layout(width, total) { placeables.forEachIndexed { index, p ->
            val span = spans.getValue(state.draft.steps[index].id)
            p.placeRelative((span.left * width).roundToInt(), rowY.getValue(span.row))
        } }
    }
}
