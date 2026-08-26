package de.thonktank.autosecretary

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import de.thonktank.autosecretary.domain.model.TaskSlot
import de.thonktank.autosecretary.presentation.alltasks.AllTasksComposeFixture
import de.thonktank.autosecretary.presentation.alltasks.AllTasksComposeHostView
import de.thonktank.autosecretary.presentation.alltasks.AllTasksPresentationState
import de.thonktank.autosecretary.presentation.alltasks.AllTasksUiState
import de.thonktank.autosecretary.presentation.alltasks.AllTasksView
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
class AllTasksComposeGoldenRobolectricTest {
    @Test
    fun characterizedComposeStatesStayWithinTheLegacyVisualContract() {
        val defaults = AllTasksComposeFixture.state()
        val scenarios = listOf(
            Scenario("standard", defaults.toggleExpanded(
                AllTasksUiState.cardKey("morning", TaskSlot.MORNING))),
            Scenario("filters-dropdown", defaults, openFilter = "SLOTS"),
            Scenario("search-title", defaults.withQuery("Statistik")),
            Scenario("search-step", defaults.withQuery("Aufgabenblatt")),
            Scenario("archived", defaults.withStatus(AllTasksUiState.Status.ARCHIVED)
                .toggleExpanded(AllTasksUiState.cardKey("archive", TaskSlot.LATER))),
            Scenario("sort-drag-targets", defaults.withMode(AllTasksUiState.Mode.SORT), drag = true),
            Scenario("empty-search", defaults.withQuery("nicht vorhanden")),
            Scenario("empty-filter", defaults.withSlots(setOf(TaskSlot.LATER))),
            Scenario("empty-status", AllTasksComposeFixture.state(false)
                .withStatus(AllTasksUiState.Status.ARCHIVED)),
            Scenario("width-320-font-1_3", defaults.toggleExpanded(
                AllTasksUiState.cardKey("morning", TaskSlot.MORNING)), 320, 1.3f),
            Scenario("width-412-font-2_0", defaults.toggleExpanded(
                AllTasksUiState.cardKey("morning", TaskSlot.MORNING)), 412, 2f),
            Scenario("width-600", defaults.toggleExpanded(
                AllTasksUiState.cardKey("morning", TaskSlot.MORNING)), 600, 1f),
            Scenario("night", defaults.toggleExpanded(
                AllTasksUiState.cardKey("morning", TaskSlot.MORNING)),
                time = LocalTime.of(23, 50), mode = DayPalette.Mode.DARK),
        )
        var firstFailure: AssertionError? = null
        scenarios.forEach { scenario ->
            val actual = render(scenario)
            try {
                GoldenAssertions.compareReadOnly(
                    AllTasksComposeGoldenRobolectricTest::class.java,
                    "/golden/all-tasks/${scenario.name}.png",
                    File("src/test/resources/golden/all-tasks", "${scenario.name}.png"),
                    File("build/reports/goldens/all-tasks-compose", scenario.name),
                    actual,
                    64,
                    .10,
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
        val palette = DayPalette.at(scenario.time, scenario.mode)
        val root = FrameLayout(activity)
        root.addView(ForestBackdropView(activity).also { it.setPalette(palette) },
            FrameLayout.LayoutParams(-1, -1))
        val host = AllTasksComposeHostView(activity)
        host.setDragSourceForTest(if (scenario.drag) "schedule:morning-MORNING" else null)
        host.openFilterForTest(scenario.openFilter)
        root.addView(host, FrameLayout.LayoutParams(-1, -1))
        activity.setContentView(root)
        host.bind(scenario.state, palette, NoopListener)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val width = dp(activity, scenario.widthDp)
        val height = dp(activity, 702)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            root.draw(Canvas(it))
            host.dispose()
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun dp(activity: ComponentActivity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    private data class Scenario(
        val name: String,
        val state: AllTasksUiState,
        val widthDp: Int = 412,
        val fontScale: Float = 1f,
        val openFilter: String? = null,
        val drag: Boolean = false,
        val time: LocalTime = LocalTime.of(9, 40),
        val mode: DayPalette.Mode = DayPalette.Mode.LIGHT,
    )

    private object NoopListener : AllTasksView.Listener
}
