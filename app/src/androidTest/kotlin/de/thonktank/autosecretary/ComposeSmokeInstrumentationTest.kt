package de.thonktank.autosecretary

import android.content.Intent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeSmokeInstrumentationTest {
    @Test
    fun transparentDebugHostCreatesAnAttachedComposition() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            ComposeSmokeActivity::class.java,
        )

        ActivityScenario.launch<ComposeSmokeActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                assertEquals(1, content.childCount)
                val composeView = content.getChildAt(0) as ComposeView
                assertTrue(composeView.isAttachedToWindow)
                assertTrue(composeView.hasComposition)
                assertFalse(composeView.isOpaque)
                assertEquals(
                    0,
                    activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                )
            }
        }
    }
}
