package de.thonktank.autosecretary;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.BlurMaskFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.View;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.ui.leaf.LeafShape;

/** Draw-only wood-grain view backed by an asynchronous, memory-bounded geometry pipeline. */
public final class WoodGrainView extends View {
    public static final class Anchor {
        public final RectF bounds;
        public final int level;

        public Anchor(RectF bounds, int level) {
            this.bounds = new RectF(bounds);
            this.level = Math.max(0, level);
        }
    }

    private final UiStyle style;
    private final LeafShape leafShape;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Anchor> anchors = Collections.emptyList();
    private List<RectF> fadedText = Collections.emptyList();
    private DayPalette palette;
    private boolean corner;
    private float cornerRatio;
    private float cornerX;
    private float cornerY;
    private WoodGrainRenderData renderData;
    private String requestedKey;
    private int requestGeneration;
    private final Path clipPath = new Path();

    public WoodGrainView(Context context) {
        this(context, null);
    }

    public WoodGrainView(Context context, LeafShape leafShape) {
        super(context);
        style = new UiStyle(context);
        this.leafShape = leafShape;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void bind(DayPalette palette, List<Anchor> anchors) {
        bind(palette, anchors, Collections.emptyList());
    }

    public void bind(DayPalette palette, List<Anchor> anchors, List<RectF> fadedText) {
        this.palette = palette;
        this.anchors = copyAnchors(anchors);
        this.fadedText = copyRects(fadedText);
        corner = false;
        requestGeometry();
    }

    public void bindCorner(DayPalette palette, float ratio) {
        bindCorner(palette, ratio, getWidth(), 0f, Collections.emptyList());
    }

    public void bindCorner(DayPalette palette, float ratio, List<RectF> fadedText) {
        bindCorner(palette, ratio, getWidth(), 0f, fadedText);
    }

    public void bindCorner(DayPalette palette, float ratio, float centerX, float centerY,
                           List<RectF> fadedText) {
        this.palette = palette;
        corner = true;
        cornerRatio = Math.max(0f, Math.min(1f, ratio));
        cornerX = centerX;
        cornerY = centerY;
        anchors = Collections.emptyList();
        this.fadedText = copyRects(fadedText);
        requestGeometry();
    }

    /** Applies the narrow cartographic text halo required above grain contours. */
    public static void applyTextHalo(TextView text, int leafColor) {
        float width = Math.min(text.getTextSize() / 4f,
                text.getResources().getDisplayMetrics().density * 3f);
        text.setShadowLayer(width, 0f, 0f, leafColor);
        text.setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        // Standalone renderers own their size lifecycle. LeafSurface renderers are bound once
        // from the wrapper's final onLayout geometry instead.
        if (leafShape == null) requestGeometry();
    }

    @Override protected void onDetachedFromWindow() {
        requestGeneration++;
        super.onDetachedFromWindow();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestGeometry();
    }

    @Override protected void onDraw(Canvas canvas) {
        WoodGrainRenderData data = renderData;
        DayPalette colors = palette;
        if (colors == null || data == null || data.strokes.isEmpty()) return;
        int clipSave = -1;
        if (leafShape != null) {
            clipSave = canvas.save();
            clipPath.reset();
            clipPath.addRoundRect(0f, 0f, getWidth(), getHeight(),
                    leafShape.radii(getResources().getDisplayMetrics().density),
                    Path.Direction.CW);
            canvas.clipPath(clipPath);
        }
        int layer = fadedText.isEmpty() ? -1
                : canvas.saveLayer(0f, 0f, getWidth(), getHeight(), null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        for (WoodGrainRenderData.Stroke stroke : data.strokes) {
            paint.setStrokeWidth(stroke.width);
            paint.setColor(UiStyle.alpha(colors.light, stroke.alpha));
            canvas.drawPath(stroke.path, paint);
        }
        if (layer >= 0) {
            fadeAroundText(canvas);
            canvas.restoreToCount(layer);
        }
        if (clipSave >= 0) canvas.restoreToCount(clipSave);
    }

    private void requestGeometry() {
        if (palette == null || getWidth() <= 0 || getHeight() <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        WoodGrainRenderRequest request;
        if (corner) {
            request = WoodGrainRenderRequest.corner(getWidth(), getHeight(), density,
                    cornerRatio, cornerX, cornerY);
        } else {
            List<WoodGrainRenderRequest.Anchor> values = new ArrayList<>();
            for (Anchor anchor : anchors)
                values.add(new WoodGrainRenderRequest.Anchor(anchor.bounds, anchor.level));
            request = WoodGrainRenderRequest.anchors(getWidth(), getHeight(), density, values);
        }
        if (request.key.equals(requestedKey) && renderData != null) {
            invalidate();
            return;
        }
        requestedKey = request.key;
        renderData = null;
        int generation = ++requestGeneration;
        WeakReference<WoodGrainView> reference = new WeakReference<>(this);
        WoodGrainRenderPipeline.request(request).whenComplete((data, failure) -> {
            WoodGrainView view = reference.get();
            if (view == null || failure != null) return;
            view.post(() -> {
                WoodGrainView target = reference.get();
                if (target == null || generation != target.requestGeneration
                        || !request.key.equals(target.requestedKey)) return;
                target.renderData = data;
                target.invalidate();
            });
        });
    }

    private void fadeAroundText(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xee000000);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        paint.setMaskFilter(new BlurMaskFilter(style.dp(2), BlurMaskFilter.Blur.NORMAL));
        for (RectF bounds : fadedText) {
            RectF area = new RectF(bounds.left - style.dp(1), bounds.top - style.dp(2),
                    bounds.right + style.dp(1), bounds.bottom + style.dp(2));
            canvas.drawRoundRect(area, style.dp(2), style.dp(2), paint);
        }
        paint.setMaskFilter(null);
        paint.setXfermode(null);
    }

    private static List<Anchor> copyAnchors(List<Anchor> values) {
        List<Anchor> result = new ArrayList<>();
        for (Anchor value : values) result.add(new Anchor(value.bounds, value.level));
        return Collections.unmodifiableList(result);
    }

    private static List<RectF> copyRects(List<RectF> values) {
        List<RectF> result = new ArrayList<>();
        for (RectF value : values) result.add(new RectF(value));
        return Collections.unmodifiableList(result);
    }

}
