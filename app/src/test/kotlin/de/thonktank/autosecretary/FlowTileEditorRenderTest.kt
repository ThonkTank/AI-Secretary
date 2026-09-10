package de.thonktank.autosecretary

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.SavedStateHandle
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy
import de.thonktank.autosecretary.presentation.editor.FlowTileEditorScreen
import org.junit.Assert.assertTrue
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

/** Review renders, not self-updating goldens. Approved Android references remain a separate gate. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h1000dp-mdpi")
class FlowTileEditorRenderTest {
    @Test fun compactAndLargeStepDialogsRenderWithTheDirectCheckbox() {
        render("step-320", 1f, true)
        render("step-320-large", 1.6f, true)
        render("tiles-320", 1f, false)
    }

    private fun render(name: String, scale: Float, form: Boolean) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = controller.get()
            val config = android.content.res.Configuration(activity.resources.configuration)
            config.fontScale = scale
            @Suppress("DEPRECATION")
            activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
            var draft = FlowEditorDraft.empty().rename("Wäsche")
            listOf("Bunt", "Weiß", "Handtuch", "Bett").forEach {
                draft = draft.addStep(it, FlowDelayPolicy.rememberLast(7_200_000L))
            }
            listOf("Aufhängen", "Abhängen", "Wegräumen").forEach {
                draft = draft.addStep(it, FlowDelayPolicy.fixed(if (it == "Aufhängen") 86_400_000L else 0L))
            }
            val ids = draft.steps.map { it.id }
            val graph = draft.graph.join(ids.take(4), ids[4]).join(listOf(ids[4]), ids[5]).join(listOf(ids[5]), ids[6])
            val vm = FlowEditorViewModel(draft.withGraph(graph), SavedStateHandle())
            if (form) vm.openStep(ids.first())
            val palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT)
            val root = ComposeView(activity).apply {
                setContent { FlowTileEditorScreen(vm.state.value, palette, vm, {}, {}) }
            }
            activity.setContentView(root)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 320, 1000)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            val bitmap = Bitmap.createBitmap(320, 1000, Bitmap.Config.ARGB_8888)
            try {
                root.draw(Canvas(bitmap))
                val file = File("build/reports/flow-tiles", "$name.png")
                requireNotNull(file.parentFile).mkdirs()
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                assertTrue(file.length() > 1000)
            } finally { bitmap.recycle() }
        } finally { controller.pause().stop().destroy() }
    }
}
