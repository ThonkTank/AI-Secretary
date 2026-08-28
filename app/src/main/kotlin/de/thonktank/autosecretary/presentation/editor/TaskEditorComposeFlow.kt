package de.thonktank.autosecretary.presentation.editor

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.EditorStepState
import de.thonktank.autosecretary.EditorUiState
import de.thonktank.autosecretary.FlowDurationDialog
import de.thonktank.autosecretary.R
import de.thonktank.autosecretary.TaskFlowDraft
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy
import de.thonktank.autosecretary.editor.TaskEditorStateReducer

@Composable
internal fun EditorFlowPage(
    state: EditorUiState,
    palette: DayPalette,
    dispatcher: TaskEditorComposeDispatcher,
) {
    val context = LocalContext.current
    Question(R.string.flow_editor_question, palette)
    EditorText(
        stringResource(R.string.flow_editor_intro),
        Color.argb(palette.muted),
        15,
        Modifier.padding(top = 8.dp, bottom = 4.dp),
        serif = false,
    )
    state.stepStates.forEachIndexed { index, step ->
        val transition = state.flowDraft.transitionAfter(step.id)
        val target = transition?.let { state.step(it.targetStepId) }
        val next = target?.text?.let {
            stringResource(R.string.flow_editor_step_next, it)
        } ?: stringResource(R.string.flow_editor_step_end)
        val wait = transition?.let { delaySummary(context, it.delay) }
        LeafSurface(
            palette = palette,
            level = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .testTag("task-editor:flow-step:${step.id}"),
            rotation = if (index % 2 == 0) -.5f else .6f,
            clickableLabel = stringResource(R.string.flow_editor_step_action, step.text),
            onClick = { showTransitionDialog(context, state, step, dispatcher) },
            padding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                EditorText((index + 1).toString(), Color.argb(palette.muted), 16, italic = true)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    EditorText(step.text, Color.argb(palette.ink), 18, maxLines = 2)
                    EditorText(next, Color.argb(palette.ink2), 15, serif = false, maxLines = 2)
                    if (wait != null) {
                        EditorText(
                            wait,
                            Color.argb(palette.muted),
                            14,
                            serif = false,
                            maxLines = 2,
                        )
                    }
                    EditorText(
                        stringResource(R.string.flow_edit),
                        Color.argb(palette.accent),
                        14,
                        Modifier.padding(top = 2.dp),
                        italic = true,
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(28.dp))
    Question(R.string.flow_editor_limited_question, palette)
    EditorText(
        stringResource(R.string.flow_editor_shared_hint),
        Color.argb(palette.muted),
        15,
        Modifier.padding(top = 8.dp),
        serif = false,
    )
    if (state.flowDraft.resources.isEmpty()) {
        EditorText(
            stringResource(R.string.flow_resources_empty),
            Color.argb(palette.muted),
            15,
            Modifier.padding(top = 12.dp),
            italic = true,
        )
    }
    state.flowDraft.resources.forEachIndexed { index, resource ->
        val editLabel = stringResource(R.string.flow_edit)
        LeafSurface(
            palette = palette,
            level = 3,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .testTag("task-editor:flow-resource:${resource.key}"),
            rotation = if (index % 2 == 0) .5f else -.4f,
            clickableLabel = "${resource.name}. $editLabel",
            onClick = { showResourceDialog(context, state, resource, dispatcher) },
            padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    EditorText(resource.name, Color.argb(palette.ink2), 17)
                    EditorText(
                        stringResource(R.string.flow_editor_resource_capacity_value, resource.capacity),
                        Color.argb(palette.muted),
                        14,
                        serif = false,
                    )
                }
                EditorText(stringResource(R.string.flow_edit), Color.argb(palette.accent), 14, italic = true)
                if (resource.persistedId == null) {
                    EditorButton(
                        text = stringResource(R.string.flow_remove),
                        palette = palette,
                        onClick = {
                            dispatcher.updateFlow(state.flowDraft.removeResource(resource.key))
                        },
                        destructive = true,
                    )
                }
            }
        }
    }
    EditorButton(
        text = stringResource(R.string.flow_resource_add),
        palette = palette,
        onClick = { showResourceDialog(context, state, null, dispatcher) },
        modifier = Modifier.padding(top = 10.dp),
    )

    Spacer(Modifier.height(24.dp))
    Question(R.string.flow_editor_rules_question, palette)
    EditorText(
        stringResource(R.string.flow_editor_rule_hint),
        Color.argb(palette.muted),
        15,
        Modifier.padding(top = 8.dp),
        serif = false,
    )
    if (state.flowDraft.leases.isEmpty()) {
        EditorText(
            stringResource(R.string.flow_capacity_empty),
            Color.argb(palette.muted),
            15,
            Modifier.padding(top = 12.dp),
            italic = true,
        )
    }
    state.flowDraft.leases.forEachIndexed { index, lease ->
        val resource = state.flowDraft.resource(lease.resourceKey)
        val acquire = state.step(lease.acquireStepId)
        val release = state.step(lease.releaseStepId)
        val missing = stringResource(R.string.flow_missing_value)
        val summary = stringResource(
            R.string.flow_lease_summary,
            lease.units,
            resource?.name ?: missing,
            acquire?.text ?: missing,
            release?.text ?: missing,
        )
        LeafSurface(
            palette = palette,
            level = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .testTag("task-editor:flow-lease:${lease.key}"),
            rotation = if (index % 2 == 0) -.5f else .5f,
            padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                EditorText(summary, Color.argb(palette.ink2), 16, serif = false)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    EditorButton(
                        text = stringResource(R.string.flow_edit),
                        palette = palette,
                        onClick = { showLeaseDialog(context, state, lease, dispatcher) },
                    )
                    EditorButton(
                        text = stringResource(R.string.flow_remove),
                        palette = palette,
                        onClick = { dispatcher.updateFlow(state.flowDraft.removeLease(lease.key)) },
                        destructive = true,
                    )
                }
            }
        }
    }
    EditorButton(
        text = stringResource(R.string.flow_capacity_add),
        palette = palette,
        onClick = { showLeaseDialog(context, state, null, dispatcher) },
        enabled = state.flowDraft.resources.isNotEmpty() && state.flowDraft.transitions.isNotEmpty(),
        modifier = Modifier.padding(top = 10.dp),
    )
    EditorText(
        stringResource(R.string.flow_setup_snapshot_note),
        Color.argb(palette.muted),
        14,
        Modifier.padding(top = 20.dp),
        serif = false,
        italic = true,
    )
}

private fun showTransitionDialog(
    context: Context,
    state: EditorUiState,
    source: EditorStepState,
    dispatcher: TaskEditorComposeDispatcher,
) {
    val current = state.flowDraft.transitionAfter(source.id)
    val candidates = state.stepStates.filter {
        it.id != source.id && state.flowDraft.canTarget(source.id, it.id)
    }
    val targetIds = listOf<String?>(null) + candidates.map { it.id }
    val targetLabels = listOf(context.getString(R.string.flow_step_end)) + candidates.map { it.text }
    val target = spinner(context, targetLabels, targetIds.indexOf(current?.targetStepId).coerceAtLeast(0))
    val modes = listOf(
        context.getString(R.string.flow_delay_immediate),
        context.getString(R.string.flow_delay_fixed),
        context.getString(R.string.flow_delay_remember),
    )
    val modeIndex = when {
        current == null || current.delay.mode == FlowDelayPolicy.Mode.FIXED &&
                current.delay.defaultDelayMillis == 0L -> 0
        current.delay.mode == FlowDelayPolicy.Mode.FIXED -> 1
        else -> 2
    }
    val delayMode = spinner(context, modes, modeIndex)
    val form = dialogForm(context)
    form.addView(labeled(context, R.string.flow_step_after, target))
    val waitRow = labeled(context, R.string.flow_step_wait, delayMode)
    form.addView(waitRow)
    fun updateWaitVisibility() {
        waitRow.visibility = if (target.selectedItemPosition == 0) View.GONE else View.VISIBLE
    }
    target.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) =
            updateWaitVisibility()
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
    updateWaitVisibility()
    val dialog = AlertDialog.Builder(context)
        .setTitle(source.text)
        .setView(form)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.step_apply, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val targetId = targetIds[target.selectedItemPosition]
            if (targetId == null) {
                dialog.dismiss()
                dispatcher.updateFlow(state.flowDraft.withTransition(source.id, null, null))
                return@setOnClickListener
            }
            val selectedMode = delayMode.selectedItemPosition
            if (selectedMode == 0) {
                dialog.dismiss()
                dispatcher.updateFlow(
                    state.flowDraft.withTransition(source.id, targetId, FlowDelayPolicy.fixed(0L)),
                )
                return@setOnClickListener
            }
            val proposed = current?.delay?.proposedDelayMillis() ?: 60L * 60L * 1_000L
            dialog.dismiss()
            FlowDurationDialog.show(
                context,
                context.getString(R.string.flow_delay_prompt_title),
                proposed,
            ) { millis ->
                val policy = if (selectedMode == 1) FlowDelayPolicy.fixed(millis)
                else FlowDelayPolicy.rememberLast(millis)
                dispatcher.updateFlow(state.flowDraft.withTransition(source.id, targetId, policy))
            }
        }
    }
    dialog.show()
}

private fun showResourceDialog(
    context: Context,
    state: EditorUiState,
    existing: TaskFlowDraft.Resource?,
    dispatcher: TaskEditorComposeDispatcher,
) {
    val form = dialogForm(context)
    val name = input(context, existing?.name.orEmpty(), InputType.TYPE_CLASS_TEXT)
    val capacity = input(
        context,
        (existing?.capacity ?: 1).toString(),
        InputType.TYPE_CLASS_NUMBER,
    )
    form.addView(labeled(context, R.string.flow_resource_name, name))
    form.addView(labeled(context, R.string.flow_resource_capacity_label, capacity))
    val dialog = AlertDialog.Builder(context)
        .setTitle(if (existing == null) R.string.flow_resource_add else R.string.flow_resource_edit)
        .setView(form)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.flow_save, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val parsed = capacity.text.toString().trim().toIntOrNull() ?: 0
            if (name.text.toString().trim().isEmpty() || parsed !in 1..1_000) {
                capacity.error = context.getString(R.string.flow_resource_invalid)
                return@setOnClickListener
            }
            val duplicate = state.flowDraft.resources.any {
                it.key != existing?.key && it.name.trim().equals(name.text.toString().trim(), true)
            }
            if (duplicate) {
                name.error = context.getString(R.string.flow_resource_invalid)
                return@setOnClickListener
            }
            val next = if (existing == null) {
                state.flowDraft.addResource(name.text.toString(), parsed)
            } else {
                state.flowDraft.updateResource(existing.key, name.text.toString(), parsed)
            }
            dialog.dismiss()
            dispatcher.updateFlow(next)
        }
    }
    dialog.show()
}

private fun showLeaseDialog(
    context: Context,
    state: EditorUiState,
    existing: TaskFlowDraft.Lease?,
    dispatcher: TaskEditorComposeDispatcher,
) {
    if (state.flowDraft.resources.isEmpty() || state.stepStates.size < 2) return
    val initialResource = state.flowDraft.resource(existing?.resourceKey)
        ?: state.flowDraft.resources.first()
    val resource = spinner(
        context,
        state.flowDraft.resources.map { it.name },
        state.flowDraft.resources.indexOf(initialResource),
    )
    val allStarts = existing == null
    val acquireLabels = (if (allStarts) listOf(context.getString(R.string.flow_lease_all_starts)) else emptyList()) +
            state.stepStates.map { it.text }
    val acquireIndex = existing?.let { lease ->
        state.stepStates.indexOfFirst { it.id == lease.acquireStepId }.coerceAtLeast(0)
    } ?: 0
    val acquire = spinner(context, acquireLabels, acquireIndex)
    val releaseIndex = existing?.let { lease ->
        state.stepStates.indexOfFirst { it.id == lease.releaseStepId }.coerceAtLeast(0)
    } ?: state.stepStates.lastIndex
    val release = spinner(context, state.stepStates.map { it.text }, releaseIndex)
    val units = input(context, (existing?.units ?: 1).toString(), InputType.TYPE_CLASS_NUMBER)
    val form = dialogForm(context)
    form.addView(labeled(context, R.string.flow_lease_resource, resource))
    form.addView(labeled(context, R.string.flow_lease_units, units))
    form.addView(labeled(context, R.string.flow_lease_acquire, acquire))
    form.addView(labeled(context, R.string.flow_lease_release, release))
    val dialog = AlertDialog.Builder(context)
        .setTitle(if (existing == null) R.string.flow_capacity_add else R.string.flow_capacity_edit)
        .setView(form)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.flow_save, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val selectedResource = state.flowDraft.resources[resource.selectedItemPosition]
            val parsedUnits = units.text.toString().trim().toIntOrNull() ?: 0
            if (parsedUnits < 1) {
                units.error = context.getString(R.string.flow_lease_invalid)
                return@setOnClickListener
            }
            if (parsedUnits > selectedResource.capacity) {
                units.error = context.getString(R.string.flow_editor_units_over_capacity)
                return@setOnClickListener
            }
            val releaseId = state.stepStates[release.selectedItemPosition].id
            var next = state.flowDraft
            if (allStarts && acquire.selectedItemPosition == 0) {
                val starts = state.flowDraft.startStepIds(state.stepStates)
                    .filter { state.flowDraft.reachableAfter(it, releaseId) }
                if (starts.isEmpty()) {
                    units.error = context.getString(R.string.flow_lease_no_starts)
                    return@setOnClickListener
                }
                starts.forEach { start ->
                    next = next.addLease(selectedResource.key, start, releaseId, parsedUnits)
                }
            } else {
                val stepIndex = acquire.selectedItemPosition - if (allStarts) 1 else 0
                val acquireId = state.stepStates[stepIndex].id
                if (!state.flowDraft.reachableAfter(acquireId, releaseId)) {
                    units.error = context.getString(R.string.flow_editor_rule_path_invalid)
                    return@setOnClickListener
                }
                next = if (existing == null) {
                    next.addLease(selectedResource.key, acquireId, releaseId, parsedUnits)
                } else {
                    next.updateLease(
                        existing.key,
                        selectedResource.key,
                        acquireId,
                        releaseId,
                        parsedUnits,
                    )
                }
            }
            dialog.dismiss()
            dispatcher.updateFlow(next)
        }
    }
    dialog.show()
}

private fun delaySummary(context: Context, policy: FlowDelayPolicy): String {
    if (policy.mode == FlowDelayPolicy.Mode.FIXED && policy.defaultDelayMillis == 0L) {
        return context.getString(R.string.flow_editor_wait_immediate)
    }
    val duration = durationText(context, policy.proposedDelayMillis())
    return context.getString(
        if (policy.mode == FlowDelayPolicy.Mode.FIXED) R.string.flow_editor_wait_fixed
        else R.string.flow_editor_wait_remember,
        duration,
    )
}

private fun durationText(context: Context, millis: Long): String {
    val minute = 60_000L
    val hour = 60L * minute
    val day = 24L * hour
    return when {
        millis >= day && millis % day == 0L -> context.resources.getQuantityString(
            R.plurals.flow_duration_days,
            (millis / day).toInt(),
            millis / day,
        )
        millis >= hour && millis % hour == 0L -> context.resources.getQuantityString(
            R.plurals.flow_duration_hours,
            (millis / hour).toInt(),
            millis / hour,
        )
        else -> {
            val minutes = (millis + minute - 1L) / minute
            context.resources.getQuantityString(
                R.plurals.flow_duration_minutes,
                minutes.toInt(),
                minutes,
            )
        }
    }
}

private fun EditorUiState.step(id: String): EditorStepState? = stepStates.firstOrNull { it.id == id }

private fun dialogForm(context: Context) = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    val padding = (22 * resources.displayMetrics.density).toInt()
    setPadding(padding, 0, padding, 0)
}

private fun labeled(context: Context, label: Int, control: View) = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    addView(TextView(context).apply { setText(label) })
    addView(control, LinearLayout.LayoutParams(-1, -2))
}

private fun input(context: Context, value: String, type: Int) = EditText(context).apply {
    inputType = type
    setText(value)
    isSingleLine = true
    setSelectAllOnFocus(true)
}

private fun spinner(context: Context, labels: List<String>, selected: Int) = Spinner(context).apply {
    adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, labels)
    setSelection(selected.coerceIn(0, labels.lastIndex.coerceAtLeast(0)))
}
