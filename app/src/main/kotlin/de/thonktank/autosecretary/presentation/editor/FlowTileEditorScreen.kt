package de.thonktank.autosecretary.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.FlowEditorForm
import de.thonktank.autosecretary.FlowEditorState
import de.thonktank.autosecretary.FlowEditorViewModel

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
    val scroll = rememberScrollState()
    var viewport by remember { mutableStateOf(Rect.Zero) }
    Column(modifier.fillMaxSize().background(Color.argb(palette.background))
        .onGloballyPositioned { viewport = it.boundsInRoot() }
        .verticalScroll(scroll).padding(16.dp).testTag("flow-editor")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            EditorButton("Abbrechen", palette, onCancel, enabled = !state.saving)
            EditorText("${state.page}/2", Color.argb(palette.muted), 16, serif = false,
                modifier = Modifier.padding(top = 14.dp))
            EditorButton("↶", palette, editor::undo,
                enabled = state.canUndo && !state.loading && !state.saving && !state.savePending && !state.loadFailed,
                contentDescription = "Rückgängig")
        }
        state.error?.let { EditorText(it, Color.argb(palette.bad), 16, serif = false,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        when {
            state.loading -> EditorText("Ablauf wird geladen …", Color.argb(palette.muted), 17, serif = false)
            state.loadFailed -> EditorButton("Erneut laden", palette, editor::retryLoad)
            state.saving -> EditorText("Wird gespeichert …", Color.argb(palette.muted), 17, serif = false)
            state.savePending -> EditorButton("Erneut speichern", palette, editor::retrySave)
            state.form != null -> FlowEditorFormSurface(state.form, state, palette, editor)
            state.page == 1 -> {
                EditorInput(state.draft.name, editor::rename, palette, hint = "Ablaufname",
                    textSize = 30, serif = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Ablaufname" })
                Spacer(Modifier.height(16.dp))
                FlowTileBoard(state, palette, editor, scroll, viewport)
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
