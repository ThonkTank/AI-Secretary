package de.thonktank.autosecretary

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.ComposeView
import de.thonktank.autosecretary.domain.model.*
import de.thonktank.autosecretary.presentation.editor.StepEditorDialog
import de.thonktank.autosecretary.presentation.today.StepEditDraft
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.Duration
import java.time.LocalTime

/** Native graphics render for review; device/IME acceptance is covered by instrumentation. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h700dp-mdpi")
class StepQuickEditorRenderTest {
    @Test fun compactDialogRendersInLightAndDarkPalettes() {
        listOf(Triple(DayPalette.Mode.LIGHT, 1f, "light"),
            Triple(DayPalette.Mode.DARK, 1f, "dark"),
            Triple(DayPalette.Mode.LIGHT, 1.6f, "large-text")).forEach { (mode, scale, name) ->
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            val activity = controller.get()
            val source = TaskStepTemplate("step", TaskId.of("task"), 0, "Rudern", 0, 0,
                StepPrescription.forAmount(StepAmount.setsReps(3, 12)), "40 kg\nSitz 7", StepActivationKind.SCHEDULED)
            val draft = StepEditDraft.from(EditableStep("execution", source, source.text, source.note, false, true))
            val root = ComposeView(activity).apply {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                        StepEditorDialog(draft, DayPalette.at(LocalTime.NOON, mode), {}, {}, {})
                    }
                }
            }
            try {
                activity.setContentView(root)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
                root.measure(exact(320), exact(700)); root.layout(0, 0, 320, 700)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
                val dialog = ShadowDialog.getLatestDialog()
                assertNotNull(dialog)
                val decor = requireNotNull(dialog.window).decorView
                decor.measure(exact(320), exact(700)); decor.layout(0, 0, 320, 700)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
                val bitmap = Bitmap.createBitmap(320, 700, Bitmap.Config.ARGB_8888)
                try {
                    decor.draw(Canvas(bitmap))
                    val file = File("build/reports/step-editor", "compact-$name.png")
                    requireNotNull(file.parentFile).mkdirs()
                    file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    assertTrue("Dialog must paint its form", file.length() > 5_000)
                } finally { bitmap.recycle() }
            } finally {
                root.disposeComposition()
                controller.pause().stop().destroy()
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
    }
    private fun exact(value: Int) = View.MeasureSpec.makeMeasureSpec(value, View.MeasureSpec.EXACTLY)
}
