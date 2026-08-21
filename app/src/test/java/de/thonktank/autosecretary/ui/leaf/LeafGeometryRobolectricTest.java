package de.thonktank.autosecretary.ui.leaf;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "mdpi")
public final class LeafGeometryRobolectricTest {
    @Test public void asymmetricRadiiAndEveryCornerCenterComeFromOneShape() {
        LeafShape shape = new LeafShape(8, 56, 10, 64);
        assertArrayEquals(new float[]{8, 8, 56, 56, 10, 10, 64, 64},
                shape.radii(1f), .001f);
        assertPoint(shape.cornerCenter(LeafShape.Corner.TOP_LEFT, 300, 200, 1), 8, 8);
        assertPoint(shape.cornerCenter(LeafShape.Corner.TOP_RIGHT, 300, 200, 1), 244, 56);
        assertPoint(shape.cornerCenter(LeafShape.Corner.BOTTOM_RIGHT, 300, 200, 1), 290, 190);
        assertPoint(shape.cornerCenter(LeafShape.Corner.BOTTOM_LEFT, 300, 200, 1), 64, 136);
    }

    @Test public void nestedAnchorBoundsStayLocalAndIncludeScrollOffsets() {
        Context context = ApplicationProvider.getApplicationContext();
        LeafSurface leaf = new LeafSurface(context, new LeafShape(8, 56, 8, 56));
        FrameLayout nested = new FrameLayout(context);
        leaf.front().addView(nested, positioned(15, 20, 200, 150));
        View anchor = new View(context);
        nested.addView(anchor, positioned(30, 90, 40, 50));
        leaf.measure(exact(300), exact(240));
        leaf.layout(0, 0, 300, 240);
        nested.scrollTo(0, 35);

        RectF bounds = leaf.localBounds(anchor);
        assertEquals(45f, bounds.left, .001f);
        assertEquals(75f, bounds.top, .001f);
        assertEquals(85f, bounds.right, .001f);
        assertEquals(125f, bounds.bottom, .001f);
    }

    private static FrameLayout.LayoutParams positioned(int left, int top, int width, int height) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = left;
        params.topMargin = top;
        return params;
    }

    private static int exact(int value) {
        return View.MeasureSpec.makeMeasureSpec(value, View.MeasureSpec.EXACTLY);
    }

    private static void assertPoint(PointF point, float x, float y) {
        assertEquals(x, point.x, .001f);
        assertEquals(y, point.y, .001f);
    }
}
