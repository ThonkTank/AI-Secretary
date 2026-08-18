package de.thonktank.autosecretary;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.LruCache;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Shared Canvas renderer for task, step, vessel and head-leaf grain. Every contour
 * is built from capsule SDFs; intersecting contours merge locally through a 3.5 dp
 * polynomial smooth minimum and are extracted with marching squares.
 */
public final class WoodGrainView extends View {
    public static final class Anchor {
        public final View view;
        public final int level;

        public Anchor(View view, int level) {
            this.view = view;
            this.level = Math.max(0, level);
        }
    }

    private static final LruCache<String, RenderData> GEOMETRY_CACHE =
            new LruCache<>(16);
    private final UiStyle style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Anchor> anchors = Collections.emptyList();
    private List<View> fadedText = Collections.emptyList();
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
        bind(palette, anchors, Collections.emptyList());
    }

    public void bind(DayPalette palette, List<Anchor> anchors, List<View> fadedText) {
        this.palette = palette;
        this.anchors = new ArrayList<>(anchors);
        this.fadedText = new ArrayList<>(fadedText);
        corner = false;
        invalidate();
    }

    public void bindCorner(DayPalette palette, float ratio) {
        bindCorner(palette, ratio, Collections.emptyList());
    }

    public void bindCorner(DayPalette palette, float ratio, List<View> fadedText) {
        this.palette = palette;
        corner = true;
        cornerRatio = Math.max(0f, Math.min(1f, ratio));
        anchors = Collections.emptyList();
        this.fadedText = new ArrayList<>(fadedText);
        invalidate();
    }

    /** Applies the narrow cartographic text halo required above grain contours. */
    static void applyTextHalo(TextView text, int leafColor) {
        float width = Math.min(text.getTextSize() / 4f,
                text.getResources().getDisplayMetrics().density * 3f);
        text.setShadowLayer(width, 0f, 0f, leafColor);
        text.setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (palette == null || getWidth() <= 0 || getHeight() <= 0) return;
        List<WoodGrainGeometry.Shape> shapes = corner ? cornerShapes() : anchorShapes();
        if (shapes.isEmpty()) return;
        float density = getResources().getDisplayMetrics().density;
        String key = cacheKey(shapes, density);
        RenderData data = GEOMETRY_CACHE.get(key);
        if (data == null) {
            data = build(shapes, density);
            GEOMETRY_CACHE.put(key, data);
        }
        int layer = canvas.saveLayer(0f, 0f, getWidth(), getHeight(), null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        for (Stroke stroke : data.strokes) {
            paint.setStrokeWidth(stroke.width);
            paint.setColor(UiStyle.alpha(palette.light, stroke.alpha));
            canvas.drawPath(stroke.path, paint);
        }
        fadeAroundText(canvas);
        canvas.restoreToCount(layer);
    }

    private RenderData build(List<WoodGrainGeometry.Shape> shapes, float density) {
        List<Stroke> result = new ArrayList<>();
        List<WoodGrainGeometry.Group> groups = WoodGrainGeometry.groups(
                getWidth(), getHeight(), density, shapes);
        float step = density * (getWidth() * getHeight() > density * density * 40_000f ? 4f : 3f);
        float smoothRadius = density * WoodGrainGeometry.SMOOTH_MIN_DP;
        for (WoodGrainGeometry.Group group : groups) {
            Path path = marchingSquares(group, step, smoothRadius);
            if (path.isEmpty()) continue;
            float depth = 0f;
            for (WoodGrainGeometry.Ring ring : group.rings) {
                float required = Math.max(1f, WoodGrainGeometry.requiredDistance(
                        getWidth(), getHeight(), ring.shape));
                depth = Math.max(depth, Math.min(1f, ring.distance / required));
            }
            result.add(new Stroke(path, (52f - 34f * depth) / 100f,
                    density * (2.2f - .9f * depth)));
        }
        return new RenderData(result);
    }

    private Path marchingSquares(WoodGrainGeometry.Group group, float step,
                                 float smoothRadius) {
        int columns = Math.max(1, (int) Math.ceil(getWidth() / step));
        int rows = Math.max(1, (int) Math.ceil(getHeight() / step));
        float[][] values = new float[rows + 1][columns + 1];
        for (int row = 0; row <= rows; row++) {
            float y = Math.min(getHeight(), row * step);
            for (int column = 0; column <= columns; column++) {
                float x = Math.min(getWidth(), column * step);
                values[row][column] = WoodGrainGeometry.groupDistance(
                        group, x, y, smoothRadius);
            }
        }
        Path path = new Path();
        Point[] crossings = new Point[4];
        for (int row = 0; row < rows; row++) {
            float top = Math.min(getHeight(), row * step);
            float bottom = Math.min(getHeight(), (row + 1) * step);
            for (int column = 0; column < columns; column++) {
                float left = Math.min(getWidth(), column * step);
                float right = Math.min(getWidth(), (column + 1) * step);
                float topLeft = values[row][column];
                float topRight = values[row][column + 1];
                float bottomRight = values[row + 1][column + 1];
                float bottomLeft = values[row + 1][column];
                int count = 0;
                if (crosses(topLeft, topRight)) crossings[count++] = new Point(
                        interpolate(left, right, topLeft, topRight), top);
                if (crosses(topRight, bottomRight)) crossings[count++] = new Point(
                        right, interpolate(top, bottom, topRight, bottomRight));
                if (crosses(bottomLeft, bottomRight)) crossings[count++] = new Point(
                        interpolate(left, right, bottomLeft, bottomRight), bottom);
                if (crosses(topLeft, bottomLeft)) crossings[count++] = new Point(
                        left, interpolate(top, bottom, topLeft, bottomLeft));
                if (count == 2) segment(path, crossings[0], crossings[1]);
                else if (count == 4) {
                    int bits = (topLeft < 0f ? 1 : 0) | (topRight < 0f ? 2 : 0)
                            | (bottomRight < 0f ? 4 : 0) | (bottomLeft < 0f ? 8 : 0);
                    float center = WoodGrainGeometry.groupDistance(group,
                            (left + right) / 2f, (top + bottom) / 2f, smoothRadius);
                    boolean adjacent = bits == 5 ? center < 0f : center >= 0f;
                    if (adjacent) {
                        segment(path, crossings[0], crossings[1]);
                        segment(path, crossings[2], crossings[3]);
                    } else {
                        segment(path, crossings[0], crossings[3]);
                        segment(path, crossings[1], crossings[2]);
                    }
                }
            }
        }
        return path;
    }

    private void fadeAroundText(Canvas canvas) {
        if (fadedText.isEmpty()) return;
        float[] own = layoutOrigin(this);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xee000000);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        paint.setMaskFilter(new BlurMaskFilter(style.dp(2), BlurMaskFilter.Blur.NORMAL));
        for (View view : fadedText) {
            if (view == null || !view.isShown() || view.getWidth() == 0) continue;
            float[] origin = layoutOrigin(view);
            RectF area = new RectF(origin[0] - own[0] - style.dp(1),
                    origin[1] - own[1] - style.dp(2),
                    origin[0] - own[0] + view.getWidth() + style.dp(1),
                    origin[1] - own[1] + view.getHeight() + style.dp(2));
            canvas.drawRoundRect(area, style.dp(2), style.dp(2), paint);
        }
        paint.setMaskFilter(null);
        paint.setXfermode(null);
    }

    private List<WoodGrainGeometry.Shape> anchorShapes() {
        List<WoodGrainGeometry.Shape> result = new ArrayList<>();
        float[] own = layoutOrigin(this);
        int element = 0;
        for (Anchor anchor : anchors) {
            View view = anchor.view;
            if (view == null || anchor.level <= 0 || !view.isShown()
                    || view.getWidth() == 0 || view.getHeight() == 0) continue;
            float[] origin = layoutOrigin(view);
            float width = view.getWidth();
            float height = view.getHeight();
            if (view instanceof DewDotView) {
                width = ((DewDotView) view).grainWidth();
                height = ((DewDotView) view).grainHeight();
            }
            result.add(new WoodGrainGeometry.Shape(element++,
                    origin[0] - own[0] + view.getWidth() / 2f,
                    origin[1] - own[1] + view.getHeight() / 2f,
                    width, height, anchor.level));
        }
        return result;
    }

    private List<WoodGrainGeometry.Shape> cornerShapes() {
        float density = getResources().getDisplayMetrics().density;
        WoodGrainGeometry.Shape unlimited = new WoodGrainGeometry.Shape(0,
                getWidth() - style.dp(46), style.dp(5), 0f, 0f, 512);
        int maximum = WoodGrainGeometry.maximumRingCount(
                getWidth(), getHeight(), unlimited, density);
        return Collections.singletonList(new WoodGrainGeometry.Shape(0,
                unlimited.cx, unlimited.cy, 0f, 0f,
                Math.round(cornerRatio * maximum)));
    }

    private String cacheKey(List<WoodGrainGeometry.Shape> shapes, float density) {
        StringBuilder key = new StringBuilder().append(getWidth()).append('x')
                .append(getHeight()).append('@').append(String.format(Locale.ROOT, "%.3f", density));
        for (WoodGrainGeometry.Shape shape : shapes)
            key.append('|').append(Math.round(shape.cx * 10f)).append(',')
                    .append(Math.round(shape.cy * 10f)).append(',')
                    .append(Math.round(shape.width * 10f)).append(',')
                    .append(Math.round(shape.height * 10f)).append(',').append(shape.level);
        return key.toString();
    }

    private static float[] layoutOrigin(View view) {
        float x = 0f;
        float y = 0f;
        View current = view;
        while (current != null) {
            x += current.getLeft();
            y += current.getTop();
            ViewParent parent = current.getParent();
            if (!(parent instanceof View)) break;
            View parentView = (View) parent;
            x -= parentView.getScrollX();
            y -= parentView.getScrollY();
            current = parentView;
        }
        return new float[]{x, y};
    }

    private static boolean crosses(float first, float second) {
        return first < 0f && second >= 0f || first >= 0f && second < 0f;
    }

    private static float interpolate(float start, float end, float first, float second) {
        float denominator = first - second;
        float fraction = denominator == 0f ? .5f : first / denominator;
        return start + Math.max(0f, Math.min(1f, fraction)) * (end - start);
    }

    private static void segment(Path path, Point first, Point second) {
        path.moveTo(first.x, first.y);
        path.lineTo(second.x, second.y);
    }

    private static final class Point {
        final float x, y;
        Point(float x, float y) { this.x = x; this.y = y; }
    }

    private static final class Stroke {
        final Path path;
        final float alpha;
        final float width;
        Stroke(Path path, float alpha, float width) {
            this.path = path;
            this.alpha = alpha;
            this.width = width;
        }
    }

    private static final class RenderData {
        final List<Stroke> strokes;
        RenderData(List<Stroke> strokes) { this.strokes = strokes; }
    }
}
