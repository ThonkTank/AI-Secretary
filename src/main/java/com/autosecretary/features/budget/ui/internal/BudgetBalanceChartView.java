package com.autosecretary.features.budget.ui.internal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import com.autosecretary.features.budget.application.overview.BudgetChartPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom view that draws a simple line chart of account balance over time.
 * Data is provided as a list of {@link BudgetChartPoint} objects (each holding a date label
 * and a balance in cents) via {@link #setPoints(List)}.
 *
 * <p>The chart auto-scales vertically to the min/max of the supplied points.
 * Only the first and last X-axis date labels are rendered to avoid overlap.
 * Chart colours are defined in {@code res/values/colors.xml} under the
 * {@code budget_chart_*} namespace.
 */
public class BudgetBalanceChartView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float density;
    private final float leftPad, rightPad, topPad, bottomPad;
    private final float lineWidth;
    private final float pointRadius;

    private List<BudgetChartPoint> points = new ArrayList<>();

    // Cached in setPoints to avoid allocations and recomputation in onDraw.
    private long cachedMin;
    private long cachedMax;
    private String cachedMinLabel;
    private String cachedMaxLabel;
    private String cachedEmptyLabel;
    private float cachedEndLabelWidth;

    public BudgetBalanceChartView(Context context) {
        this(context, null);
    }

    public BudgetBalanceChartView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BudgetBalanceChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = context.getResources().getDisplayMetrics().density;
        android.content.res.Resources res = context.getResources();
        lineWidth = res.getDimension(R.dimen.budget_chart_line_width);
        pointRadius = res.getDimension(R.dimen.budget_chart_point_radius);

        int chartLine = ContextCompat.getColor(context, R.color.budget_chart_line);
        linePaint.setColor(chartLine);
        linePaint.setStrokeWidth(lineWidth);
        linePaint.setStyle(Paint.Style.STROKE);

        pointPaint.setColor(chartLine);
        pointPaint.setStyle(Paint.Style.FILL);

        axisPaint.setColor(ContextCompat.getColor(context, R.color.budget_chart_axis));
        axisPaint.setStrokeWidth(dp(1f));

        labelPaint.setColor(ContextCompat.getColor(context, R.color.budget_chart_label));
        // Use getDimension for sp-based text size so chart labels respect font scaling.
        labelPaint.setTextSize(res.getDimension(R.dimen.budget_chart_label_text_size));

        leftPad   = dp(16f);
        rightPad  = dp(12f);
        topPad    = dp(12f);
        bottomPad = dp(28f);
    }

    public void setPoints(List<BudgetChartPoint> points) {
        this.points = points != null ? points : new ArrayList<>();
        cacheDrawData();
        updateAccessibilityDescription();
        invalidate();
    }

    /**
     * Pre-computes min/max, label strings, and text widths so onDraw allocates nothing.
     */
    private void cacheDrawData() {
        cachedEmptyLabel = getContext().getString(R.string.budget_chart_no_data);
        if (points.isEmpty()) {
            return;
        }
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (BudgetChartPoint p : points) {
            min = Math.min(min, p.balanceCents());
            max = Math.max(max, p.balanceCents());
        }
        // Guard against flat series: expand by 1 cent so the denominator is never zero.
        if (max == min) {
            max += 1;
            min -= 1;
        }
        cachedMin = min;
        cachedMax = max;
        cachedMinLabel = CurrencyFormatter.euros(min);
        cachedMaxLabel = CurrencyFormatter.euros(max);
        BudgetChartPoint last = points.get(points.size() - 1);
        cachedEndLabelWidth = labelPaint.measureText(last.label());
    }

    /**
     * Updates contentDescription so screen readers announce a meaningful summary
     * (date range + min/max balance) instead of just the static chart title.
     */
    private void updateAccessibilityDescription() {
        if (points.isEmpty()) {
            setContentDescription(cachedEmptyLabel);
            return;
        }
        String first = points.get(0).label();
        String last = points.get(points.size() - 1).label();
        // "Saldo-Verlauf: 01.01 bis 31.01, Min 1.200,00 €, Max 3.400,00 €"
        setContentDescription(
                getContext().getString(R.string.budget_chart_title)
                        + ": " + first + " – " + last
                        + ", Min " + cachedMinLabel
                        + ", Max " + cachedMaxLabel);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth() - leftPad - rightPad;
        float height = getHeight() - topPad - bottomPad;
        if (width <= 0 || height <= 0) return;

        float bottomY = topPad + height;
        canvas.drawLine(leftPad, bottomY, leftPad + width, bottomY, axisPaint);

        if (points.isEmpty()) {
            canvas.drawText(cachedEmptyLabel, leftPad, topPad + height / 2f, labelPaint);
            return;
        }

        long min = cachedMin;
        long max = cachedMax;

        float stepX = points.size() > 1 ? width / (points.size() - 1) : 0;
        float lastX = -1;
        float lastY = -1;

        for (int i = 0; i < points.size(); i++) {
            BudgetChartPoint point = points.get(i);
            float x = leftPad + i * stepX;
            // Normalise balance to [0,1] then invert: ratio=1 → top of chart (high balance).
            float ratio = (point.balanceCents() - min) / (float) (max - min);
            float y = bottomY - ratio * height;

            if (i > 0) {
                canvas.drawLine(lastX, lastY, x, y, linePaint);
            }
            canvas.drawCircle(x, y, pointRadius, pointPaint);
            lastX = x;
            lastY = y;
        }

        canvas.drawText(cachedMaxLabel, leftPad, topPad + dp(2f), labelPaint);
        canvas.drawText(cachedMinLabel, leftPad, bottomY - dp(4f), labelPaint);

        BudgetChartPoint first = points.get(0);
        BudgetChartPoint last = points.get(points.size() - 1);
        canvas.drawText(first.label(), leftPad, getHeight() - dp(8f), labelPaint);
        canvas.drawText(last.label(), leftPad + width - cachedEndLabelWidth, getHeight() - dp(8f), labelPaint);
    }

    private float dp(float value) {
        return value * density;
    }
}
