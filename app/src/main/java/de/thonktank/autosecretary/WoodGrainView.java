package de.thonktank.autosecretary;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Canvas-native growth rings. Unioned contours merge instead of crossing. */
public final class WoodGrainView extends View {
    public static final class Anchor {
        public final View view;
        public final int level;
        public Anchor(View view, int level) { this.view = view; this.level = Math.max(0, level); }
    }

    private final UiStyle style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Anchor> anchors = Collections.emptyList();
    private DayPalette palette;
    private boolean corner;
    private float cornerRatio;

    public WoodGrainView(Context context) {
        super(context);
        style = new UiStyle(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void bind(DayPalette palette, List<Anchor> anchors) {
        this.palette = palette;
        this.anchors = new ArrayList<>(anchors);
        corner = false;
        invalidate();
    }

    public void bindCorner(DayPalette palette, float ratio) {
        this.palette = palette;
        corner = true;
        cornerRatio = Math.max(0f, Math.min(1f, ratio));
        anchors = Collections.emptyList();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (palette == null || getWidth() == 0 || getHeight() == 0) return;
        List<Shape> shapes = corner ? cornerShapes() : anchorShapes();
        int max = 0;
        for (Shape shape : shapes) max = Math.max(max, shape.level);
        for (int ring = max - 1; ring >= 0; ring--) {
            Path union = null;
            float widest = 0f;
            for (Shape shape : shapes) {
                if (shape.level <= ring) continue;
                float distance = width(ring);
                widest = Math.max(widest, distance);
                Path expanded = capsule(shape, distance);
                if (union == null) union = expanded;
                else union.op(expanded, Path.Op.UNION);
            }
            if (union == null) continue;
            float need = Math.max(getWidth(), getHeight());
            float depth = Math.min(1f, widest / Math.max(1f, need));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(style.dp(2.2f - .9f * depth));
            paint.setColor(UiStyle.alpha(palette.light, (52f - 34f * depth) / 100f));
            canvas.drawPath(union, paint);
        }
    }

    private List<Shape> anchorShapes() {
        List<Shape> result = new ArrayList<>();
        for (Anchor anchor : anchors) {
            if (anchor.view == null || anchor.level <= 0 || !anchor.view.isShown()) continue;
            int[] here = new int[2], there = new int[2];
            getLocationOnScreen(here); anchor.view.getLocationOnScreen(there);
            result.add(new Shape(there[0] - here[0], there[1] - here[1],
                    anchor.view.getWidth(), anchor.view.getHeight(), anchor.level));
        }
        return result;
    }

    private List<Shape> cornerShapes() {
        float cx = getWidth() - style.dp(46), cy = style.dp(5);
        float need = Math.max(cx, Math.max(getWidth() - cx,
                Math.max(cy, getHeight() - cy)));
        int max = 0;
        while (max < 80 && width(max) < need) max++;
        int rings = Math.round(cornerRatio * max);
        return Collections.singletonList(new Shape(cx, cy, 0, 0, rings));
    }

    private Path capsule(Shape shape, float grow) {
        RectF box = new RectF(shape.x - grow, shape.y - grow,
                shape.x + shape.w + grow, shape.y + shape.h + grow);
        float radius = Math.min(box.width(), box.height()) / 2f;
        Path path = new Path();
        path.addRoundRect(box, radius, radius, Path.Direction.CW);
        return path;
    }

    private float width(int i) { return style.dp(7f + 6.9f * i + .39f * i * i); }

    private static final class Shape {
        final float x, y, w, h;
        final int level;
        Shape(float x, float y, float w, float h, int level) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.level = level;
        }
    }
}
