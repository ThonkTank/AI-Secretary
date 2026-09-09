package de.thonktank.autosecretary

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import de.thonktank.autosecretary.domain.model.FlowRunSummary
import de.thonktank.autosecretary.presentation.flowruns.FlowRunsComposeCallbacks
import de.thonktank.autosecretary.presentation.flowruns.FlowRunsComposeFixture
import de.thonktank.autosecretary.presentation.flowruns.FlowRunsComposeHostView
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h702dp-xhdpi")
class FlowRunsComposeGoldenRobolectricTest {
    @Test
    fun mobileFlowRunStatesMatchReviewedGoldens() {
        val now = System.currentTimeMillis()
        val scenarios = listOf(
            Scenario("error", FlowRunsComposeFixture.error()),
            Scenario("standard", FlowRunsComposeFixture.state(now)),
            Scenario("changing", FlowRunsComposeFixture.changing(now)),
            Scenario("loading", FlowRunsComposeFixture.loading()),
            Scenario("empty", FlowRunsComposeFixture.empty()),
            Scenario("width-320", FlowRunsComposeFixture.state(now), widthDp = 320),
            Scenario("font-2_0", FlowRunsComposeFixture.state(now), fontScale = 2f),
            Scenario(
                "night",
                FlowRunsComposeFixture.state(now),
                time = LocalTime.of(23, 50),
                mode = DayPalette.Mode.DARK,
            ),
        )
        var firstFailure: AssertionError? = null
        scenarios.forEach { scenario ->
            val actual = render(scenario)
            try {
                GoldenAssertions.compare(
                    FlowRunsComposeGoldenRobolectricTest::class.java,
                    "/golden/flow-runs/${scenario.name}.png",
                    File("src/test/resources/golden/flow-runs", "${scenario.name}.png"),
                    File("build/reports/goldens/flow-runs-compose", scenario.name),
                    actual,
                    64,
                    .10,
                    "UPDATE_FLOW_RUNS_GOLDENS",
                )
            } catch (failure: AssertionError) {
                if (firstFailure == null) firstFailure = failure
            } finally {
                actual.recycle()
            }
        }
        firstFailure?.let { throw it }
    }

    private fun render(scenario: Scenario): Bitmap {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val configuration = Configuration(activity.resources.configuration).apply {
            screenWidthDp = scenario.widthDp
            screenHeightDp = 702
            fontScale = scenario.fontScale
        }
        @Suppress("DEPRECATION")
        activity.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
        val host = FlowRunsComposeHostView(activity)
        activity.setContentView(host)
        host.bind(scenario.state, DayPalette.at(scenario.time, scenario.mode), NoopCallbacks)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val width = dp(activity, scenario.widthDp)
        val height = dp(activity, 702)
        host.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, width, height)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            host.draw(Canvas(it))
            host.dispose()
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun dp(activity: ComponentActivity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    private data class Scenario(
        val name: String,
        val state: FlowRunsScreenState,
        val widthDp: Int = 412,
        val fontScale: Float = 1f,
        val time: LocalTime = LocalTime.of(9, 40),
        val mode: DayPalette.Mode = DayPalette.Mode.LIGHT,
    )

    private object NoopCallbacks : FlowRunsComposeCallbacks {
        override fun onBack() = Unit
        override fun onDefer(run: FlowRunSummary) = Unit
        override fun onPostpone(run: FlowRunSummary) = Unit
        override fun onReadyNow(run: FlowRunSummary) = Unit
        override fun onAdjustTime(run: FlowRunSummary) = Unit
        override fun onMoveBefore(runId: String, beforeRunId: String?) = Unit
        override fun onCancel(run: FlowRunSummary) = Unit
        override fun onDismissError(errorId: Long) = Unit
    }
}
