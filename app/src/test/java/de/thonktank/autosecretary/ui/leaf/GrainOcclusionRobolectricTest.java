package de.thonktank.autosecretary.ui.leaf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "mdpi")
public final class GrainOcclusionRobolectricTest {
    @Test public void shortTextOccludesItsLayoutLineInsteadOfWeightedViewRemainder() {
        Scene scene = new Scene();
        TextView text = scene.text("Kurz", 20, 15, 240, 54);
        text.setPadding(12, 5, 16, 7);
        scene.layout();

        List<RectF> lines = GrainOcclusion.text(text).resolve(scene.leaf);

        assertEquals(1, lines.size());
        RectF view = scene.leaf.localBounds(text);
        assertTrue(lines.get(0).left >= view.left + text.getCompoundPaddingLeft());
        assertTrue(lines.get(0).right < view.right - 100f);
        assertTrue(lines.get(0).right <= view.right - text.getCompoundPaddingRight());
    }

    @Test public void blankLayoutLineCreatesNoOcclusionBetweenVisibleLines() {
        Scene scene = new Scene();
        TextView text = scene.text("Alpha\n   \nBeta", 20, 15, 200, 120);
        scene.layout();

        List<RectF> lines = GrainOcclusion.text(text).resolve(scene.leaf);

        assertEquals(2, lines.size());
        assertTrue(lines.get(1).top > lines.get(0).bottom);
        assertTrue(lines.get(0).width() < text.getWidth());
        assertTrue(lines.get(1).width() < text.getWidth());
    }

    @Test public void multilineTextHonorsPaddingVerticalGravityAndScroll() {
        Scene scene = new Scene();
        TextView text = scene.text("Erste Zeile\nZweite", 30, 20, 190, 110);
        text.setPadding(14, 8, 10, 8);
        text.setGravity(Gravity.CENTER_VERTICAL);
        scene.layout();
        List<RectF> before = GrainOcclusion.text(text).resolve(scene.leaf);
        text.scrollTo(6, 0);
        List<RectF> after = GrainOcclusion.text(text).resolve(scene.leaf);

        assertEquals(2, before.size());
        float available = text.getHeight() - text.getExtendedPaddingTop()
                - text.getExtendedPaddingBottom();
        float expectedOffset = Math.max(0f, available - text.getLayout().getHeight()) / 2f;
        float expectedTop = scene.leaf.localBounds(text).top + text.getExtendedPaddingTop()
                + expectedOffset + text.getLayout().getLineTop(0);
        assertEquals(expectedTop, before.get(0).top, .01f);
        assertEquals(before.get(0).left - 6f, after.get(0).left, .01f);
        assertEquals(before.get(0).right - 6f, after.get(0).right, .01f);
    }

    @Test public void ellipsizedLineAndClippedAncestorStayInsideVisibleBounds() {
        Scene scene = new Scene();
        FrameLayout clip = new FrameLayout(scene.activity);
        clip.setClipChildren(true);
        scene.leaf.front().addView(clip, positioned(40, 20, 110, 60));
        TextView text = new TextView(scene.activity);
        text.setText("Eine absichtlich sehr lange ellipsierte Zeile");
        text.setTextSize(18);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        clip.addView(text, positioned(55, 5, 120, 45));
        scene.layout();

        List<RectF> lines = GrainOcclusion.text(text).resolve(scene.leaf);

        assertEquals(1, lines.size());
        RectF clipBounds = scene.leaf.localBounds(clip);
        assertTrue(lines.get(0).left >= clipBounds.left);
        assertTrue(lines.get(0).right <= clipBounds.right);
        assertTrue(lines.get(0).width() > 0f);
    }

    private static FrameLayout.LayoutParams positioned(int left, int top, int width, int height) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = left;
        params.topMargin = top;
        return params;
    }

    private static final class Scene {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final LeafSurface leaf = new LeafSurface(activity, new LeafShape(8, 56, 8, 56));

        Scene() {
            activity.setContentView(leaf, new FrameLayout.LayoutParams(300, 240));
        }

        TextView text(String value, int left, int top, int width, int height) {
            TextView text = new TextView(activity);
            text.setText(value);
            text.setTextSize(18);
            leaf.front().addView(text, positioned(left, top, width, height));
            return text;
        }

        void layout() {
            leaf.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY));
            leaf.layout(0, 0, 300, 240);
        }
    }
}
