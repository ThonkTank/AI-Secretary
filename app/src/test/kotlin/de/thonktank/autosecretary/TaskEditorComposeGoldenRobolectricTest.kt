package de.thonktank.autosecretary

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import de.thonktank.autosecretary.presentation.editor.TaskEditorComposeHostView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.time.LocalTime
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy
import de.thonktank.autosecretary.domain.model.Recurrence
import de.thonktank.autosecretary.domain.model.StepAmount
import de.thonktank.autosecretary.domain.model.StepActivationKind
import de.thonktank.autosecretary.domain.model.StepPrescription
import de.thonktank.autosecretary.domain.model.TaskBoundKind
import de.thonktank.autosecretary.domain.model.TaskSlot
import de.thonktank.autosecretary.domain.model.TimeOfDay

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xhdpi")
class TaskEditorComposeGoldenRobolectricTest {
    @Test
    fun allTenComposeWizardStatesStayWithinTheApprovedVisualContract() {
        var firstFailure: AssertionError? = null
        TaskEditorGoldenScenario.ALL.forEach { scenario ->
            val actual = render(scenario)
            try {
                GoldenAssertions.compareReadOnly(
                    TaskEditorComposeGoldenRobolectricTest::class.java,
                    "/golden/task-editor/wizard/${scenario.id}.png",
                    File("src/test/resources/golden/task-editor/wizard", "${scenario.id}.png"),
                    File("build/reports/goldens/task-editor-compose", scenario.id),
                    actual,
                    64,
                    .25,
                )
            } catch (failure: AssertionError) {
                if (firstFailure == null) firstFailure = failure
            } finally {
                actual.recycle()
            }
        }
        firstFailure?.let { throw it }
    }

    @Test
    fun nightAndCompactComposeStatesStayWithinTheApprovedAdaptiveContract() {
        var firstFailure: AssertionError? = null
        TaskEditorAdaptiveGoldenScenario.ALL.forEach { scenario ->
            val actual = render(scenario)
            try {
                GoldenAssertions.compareReadOnly(
                    TaskEditorComposeGoldenRobolectricTest::class.java,
                    "/golden/task-editor/adaptive/${scenario.id}.png",
                    File("src/test/resources/golden/task-editor/adaptive", "${scenario.id}.png"),
                    File("build/reports/goldens/task-editor-compose-adaptive", scenario.id),
                    actual,
                    64,
                    .25,
                )
            } catch (failure: AssertionError) {
                if (firstFailure == null) firstFailure = failure
            } finally {
                actual.recycle()
            }
        }
        firstFailure?.let { throw it }
    }

    @Test
    fun optionalLaundryFlowStaysWithinItsApprovedVisualContract() {
        val actual = render(flowState())
        try {
            GoldenAssertions.compareReadOnly(
                TaskEditorComposeGoldenRobolectricTest::class.java,
                "/golden/task-editor/flow/01-waesche-ablauf.png",
                File("src/test/resources/golden/task-editor/flow", "01-waesche-ablauf.png"),
                File("build/reports/goldens/task-editor-compose-flow", "01-waesche-ablauf"),
                actual,
                64,
                .25,
            )
        } finally {
            actual.recycle()
        }
    }

    private fun render(scenario: TaskEditorGoldenScenario): Bitmap {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT)
        val root = FrameLayout(activity)
        root.addView(ForestBackdropView(activity).also { it.setPalette(palette) },
            FrameLayout.LayoutParams(-1, -1))
        val editor = TaskEditorComposeHostView(activity)
        editor.setContentInsets(dp(activity, 28), 0)
        root.addView(editor, FrameLayout.LayoutParams(-1, -1))
        activity.setContentView(root)
        editor.bind(scenario.state(), palette, TaskEditorGoldenScenario.TODAY, NoopListener)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        root.measure(
            View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1784, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 824, 1784)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val bitmap = Bitmap.createBitmap(824, 1784, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        root.draw(canvas)
        editor.dispose()
        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
        return bitmap
    }

    private fun render(state: EditorUiState): Bitmap {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT)
        val root = FrameLayout(activity)
        root.addView(
            ForestBackdropView(activity).also { it.setPalette(palette) },
            FrameLayout.LayoutParams(-1, -1),
        )
        val editor = TaskEditorComposeHostView(activity)
        editor.setContentInsets(dp(activity, 28), 0)
        root.addView(editor, FrameLayout.LayoutParams(-1, -1))
        activity.setContentView(root)
        editor.bind(state, palette, TaskEditorGoldenScenario.TODAY, NoopListener)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        root.measure(
            View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1784, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 824, 1784)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val bitmap = Bitmap.createBitmap(824, 1784, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        editor.dispose()
        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
        return bitmap
    }

    private fun flowState(): EditorUiState {
        val steps = listOf(
            editorStep("wash", "Waschmaschine anmachen"),
            editorStep("hang", "Wäsche aufhängen"),
            editorStep("take-down", "Wäsche abnehmen"),
        )
        val flow = TaskFlowDraft.empty()
            .withTransition("wash", "hang", FlowDelayPolicy.rememberLast(7_200_000L))
            .withTransition("hang", "take-down", FlowDelayPolicy.fixed(86_400_000L))
            .addResource("Wäscheständer", 2)
            .addLease("draft-resource:1", "wash", "take-down", 1)
        return EditorUiState.create().draft(
            "Wäsche",
            TaskSlot.MORNING,
            15,
            Recurrence.DAILY,
            1,
            0,
            TimeOfDay.MORNING.bit,
            TaskBoundKind.FOREVER,
            null,
            null,
            null,
            null,
            "",
            steps,
            null,
            1,
        ).withFlowDraft(flow).withPage(EditorUiState.Page.FLOW, false)
    }

    private fun editorStep(id: String, text: String) = EditorStepState(
        id, text, StepCadenceMode.ALWAYS, 0, null,
        StepPrescription.forAmount(StepAmount.none()), null, "", StepActivationKind.SCHEDULED,
    )

    private fun render(scenario: TaskEditorAdaptiveGoldenScenario): Bitmap {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.get()
        controller.setup()
        val configuration = Configuration(activity.resources.configuration).apply {
            screenWidthDp = scenario.widthDp
            screenHeightDp = scenario.heightDp
            fontScale = scenario.fontScale
        }
        @Suppress("DEPRECATION")
        activity.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
        val palette = DayPalette.at(scenario.time, scenario.paletteMode)
        val root = FrameLayout(activity)
        root.addView(ForestBackdropView(activity).also { it.setPalette(palette) },
            FrameLayout.LayoutParams(-1, -1))
        val editor = TaskEditorComposeHostView(activity)
        editor.setContentInsets(dp(activity, 28), 0)
        root.addView(editor, FrameLayout.LayoutParams(-1, -1))
        activity.setContentView(root)
        editor.bind(scenario.state(), palette, TaskEditorGoldenScenario.TODAY, NoopListener)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val width = dp(activity, scenario.widthDp)
        val height = dp(activity, scenario.heightDp)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        editor.dispose()
        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
        return bitmap
    }

    private fun dp(activity: ComponentActivity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    private object NoopListener : TaskEditorComposeHostView.Listener {
        override fun onDraftChanged(draft: EditorUiState) = Unit
        override fun onSave(draft: EditorUiState) = Unit
        override fun onDelete(taskId: String) = Unit
        override fun onDismiss() = Unit
    }
}
