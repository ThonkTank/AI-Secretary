package de.thonktank.autosecretary.ui.leaf;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.UiStyle;

/** Owns a leaf's outline, clipping, shadow, grain and front content coordinate space. */
public class LeafSurface extends FrameLayout {
    private final LeafShape shape;
    private final WoodGrainView grain;
    private final FrameLayout front;
    private GrainSpec grainSpec;
    private DayPalette palette;

    public LeafSurface(Context context, LeafShape shape) {
        super(context);
        if (shape == null) throw new IllegalArgumentException("shape == null");
        this.shape = shape;
        setClipChildren(true);
        grain = new WoodGrainView(context, shape);
        super.addView(grain, new LayoutParams(-1, -1));
        front = new FrameLayout(context);
        super.addView(front, new LayoutParams(-1, -2));
    }

    public FrameLayout front() { return front; }

    public void bindSurface(DayPalette palette, int fill, int edge,
                            float elevationDp, float shadowStrength) {
        this.palette = palette;
        setBackground(shape.drawable(getContext(), fill, edge, 1f));
        float density = getResources().getDisplayMetrics().density;
        setElevation(elevationDp * density);
        if (Build.VERSION.SDK_INT >= 28) {
            int shadow = UiStyle.alpha(0xff000000,
                    Math.min(1f, palette.shadowAlpha * shadowStrength));
            setOutlineSpotShadowColor(shadow);
            setOutlineAmbientShadowColor(shadow);
        }
        requestLayout();
    }

    public void setGrainSpec(GrainSpec grainSpec) {
        this.grainSpec = grainSpec;
        requestLayout();
    }

    public LeafShape shape() { return shape; }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        LayoutParams frontParams = (LayoutParams) front.getLayoutParams();
        int horizontal = getPaddingLeft() + getPaddingRight()
                + frontParams.leftMargin + frontParams.rightMargin;
        int vertical = getPaddingTop() + getPaddingBottom()
                + frontParams.topMargin + frontParams.bottomMargin;
        int frontWidth = getChildMeasureSpec(widthMeasureSpec, horizontal, frontParams.width);
        int frontHeight = getChildMeasureSpec(heightMeasureSpec, vertical, frontParams.height);
        front.measure(frontWidth, frontHeight);
        int width = resolveSize(Math.max(getSuggestedMinimumWidth(),
                front.getMeasuredWidth() + horizontal), widthMeasureSpec);
        int height = resolveSize(Math.max(getSuggestedMinimumHeight(),
                front.getMeasuredHeight() + vertical), heightMeasureSpec);
        setMeasuredDimension(width, height);
        grain.measure(MeasureSpec.makeMeasureSpec(width - getPaddingLeft() - getPaddingRight(),
                        MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height - getPaddingTop() - getPaddingBottom(),
                        MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        bindGrainForFinalLayout();
    }

    private void bindGrainForFinalLayout() {
        GrainSpec spec = grainSpec;
        DayPalette colors = palette;
        if (spec == null || colors == null || getWidth() <= 0 || getHeight() <= 0) return;
        List<RectF> faded = visibleBounds(spec.fadedText);
        if (spec instanceof GrainSpec.Corner) {
            GrainSpec.Corner corner = (GrainSpec.Corner) spec;
            PointF center = shape.cornerCenter(corner.corner, getWidth(), getHeight(),
                    getResources().getDisplayMetrics().density);
            grain.bindCorner(colors, corner.ratio, center.x, center.y, faded);
            return;
        }
        GrainSpec.Anchors anchors = (GrainSpec.Anchors) spec;
        List<WoodGrainView.Anchor> resolved = new ArrayList<>();
        for (GrainSpec.Anchor anchor : anchors.anchors) {
            RectF bounds = localBounds(anchor.target);
            if (anchor.width > 0f && anchor.height > 0f) {
                float centerX = bounds.centerX();
                float centerY = bounds.centerY();
                bounds.set(centerX - anchor.width / 2f, centerY - anchor.height / 2f,
                        centerX + anchor.width / 2f, centerY + anchor.height / 2f);
            }
            resolved.add(new WoodGrainView.Anchor(bounds, anchor.level));
        }
        grain.bind(colors, resolved, faded);
    }

    private List<RectF> visibleBounds(List<View> views) {
        if (views.isEmpty()) return Collections.emptyList();
        List<RectF> result = new ArrayList<>();
        for (View view : views) {
            if (view != null && view.isShown() && view.getWidth() > 0 && view.getHeight() > 0)
                result.add(localBounds(view));
        }
        return result;
    }

    RectF localBounds(View descendant) {
        if (!isDescendant(descendant))
            throw new IllegalArgumentException("Grain target must be inside its LeafSurface");
        float left = 0f;
        float top = 0f;
        View current = descendant;
        while (current != this) {
            left += current.getLeft();
            top += current.getTop();
            android.view.ViewParent parent = current.getParent();
            if (!(parent instanceof View))
                throw new IllegalArgumentException("Grain target must be inside its LeafSurface");
            View parentView = (View) parent;
            left -= parentView.getScrollX();
            top -= parentView.getScrollY();
            current = parentView;
        }
        return new RectF(left, top, left + descendant.getWidth(), top + descendant.getHeight());
    }

    private boolean isDescendant(View target) {
        View current = target;
        while (current != null) {
            if (current == this) return true;
            android.view.ViewParent parent = current.getParent();
            if (!(parent instanceof View)) return false;
            current = (View) parent;
        }
        return false;
    }

}
