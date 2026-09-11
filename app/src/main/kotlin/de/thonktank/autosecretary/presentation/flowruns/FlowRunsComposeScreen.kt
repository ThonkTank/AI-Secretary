package de.thonktank.autosecretary.presentation.flowruns

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.FlowRunsScreenState
import de.thonktank.autosecretary.R
import de.thonktank.autosecretary.presentation.mobile.MobileText
import de.thonktank.autosecretary.presentation.mobile.mobileColor
import de.thonktank.autosecretary.presentation.mobile.mobileLeaf

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FlowRunsComposeScreen(
    state: FlowRunsScreenState,
    palette: DayPalette,
    callbacks: FlowRunsComposeCallbacks,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(mobileColor(palette.background))
            .testTag(FlowRunsSemantics.SCREEN)
            .semantics { testTagsAsResourceId = true },
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            FlowRunsHeader(palette, callbacks::onBack)
        }
        if (state.runs.none { it.steps.isNotEmpty() }) item(key = "intro") {
            MobileText(
                stringResource(R.string.flow_runs_description),
                mobileColor(palette.ink2),
                16,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (state.changing) {
            item(key = "changing") {
                FlowStatusPanel(
                    text = stringResource(R.string.flow_changing),
                    palette = palette,
                    tag = FlowRunsSemantics.CHANGING,
                )
            }
        }
        if (state.loading) {
            item(key = "loading") {
                FlowStatusPanel(
                    text = stringResource(R.string.flow_loading),
                    palette = palette,
                    tag = FlowRunsSemantics.LOADING,
                )
            }
        }
        if (state.errorMessage != null) {
            item(key = "error") {
                FlowErrorPanel(
                    text = state.errorMessage,
                    errorId = state.errorId,
                    palette = palette,
                    onDismiss = callbacks::onDismissError,
                )
            }
        }
        if (!state.loading && state.errorMessage == null && state.runs.isEmpty()) {
            item(key = "empty") {
                FlowStatusPanel(
                    text = stringResource(R.string.flow_runs_empty),
                    palette = palette,
                    tag = FlowRunsSemantics.EMPTY,
                )
            }
        } else {
            itemsIndexed(state.runs, key = { _, run -> run.id }) { index, run ->
                FlowRunCard(
                    run = run,
                    index = index,
                    runs = state.runs,
                    busy = state.changing,
                    palette = palette,
                    nowEpochMillis = state.nowEpochMillis,
                    callbacks = callbacks,
                )
            }
        }
    }
}

@Composable
private fun FlowRunsHeader(palette: DayPalette, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val label = stringResource(R.string.flow_back)
        Box(
            modifier = Modifier
                .size(48.dp)
                .mobileLeaf(palette, RoundedCornerShape(24.dp), level = 1)
                .testTag(FlowRunsSemantics.BACK)
                .semantics { contentDescription = label; role = Role.Button }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            MobileText("‹", mobileColor(palette.ink), 32, serif = true)
        }
        MobileText(
            stringResource(R.string.flow_runs_title),
            mobileColor(palette.ink),
            29,
            modifier = Modifier.weight(1f),
            serif = true,
        )
    }
}
