package com.autosecretary.features.budget.ui.internal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import com.autosecretary.features.budget.ui.state.BudgetChartPoint;

import java.util.ArrayList;
import java.util.List;

public class BudgetBalanceChartView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float density;
    private float leftPad, rightPad, topPad, bottomPad;

    private List<BudgetChartPoint> points = new ArrayList<>();

    public BudgetBalanceChartView(Context context) {
        this(context, null);
    }

    public BudgetBalanceChartView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BudgetBalanceChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = context.getResources().getDisplayMetrics().density;
        int chartLine = ContextCompat.getColor(context, R.color.budget_chart_line);
        linePaint.setColor(chartLine);
        linePaint.setStrokeWidth(dp(2f));
        linePaint.setStyle(Paint.Style.STROKE);

        pointPaint.setColor(chartLine);
        pointPaint.setStyle(Paint.Style.FILL);

        axisPaint.setColor(ContextCompat.getColor(context, R.color.budget_chart_axis));
        axisPaint.setStrokeWidth(dp(1f));

        labelPaint.setColor(ContextCompat.getColor(context, R.color.budget_chart_label));
        labelPaint.setTextSize(dp(10f));

        leftPad   = dp(16f);
        rightPad  = dp(12f);
        topPad    = dp(12f);
        bottomPad = dp(28f);
    }

    public void setPoints(List<BudgetChartPoint> points) {
        this.points = points != null ? points : new ArrayList<>();
        invalidate();
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
            canvas.drawText(getContext().getString(R.string.budget_chart_no_data), leftPad, topPad + height / 2f, labelPaint);
            return;
        }

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (BudgetChartPoint point : points) {
            min = Math.min(min, point.getBalanceCents());
            max = Math.max(max, point.getBalanceCents());
        }
        if (max == min) {
            max += 1;
            min -= 1;
        }

        float stepX = points.size() > 1 ? (width / (points.size() - 1)) : 0;
        float lastX = -1;
        float lastY = -1;

        for (int i = 0; i < points.size(); i++) {
            BudgetChartPoint point = points.get(i);
            float x = leftPad + i * stepX;
            float ratio = (point.getBalanceCents() - min) / (float) (max - min);
            float y = bottomY - ratio * height;

            if (i > 0) {
                canvas.drawLine(lastX, lastY, x, y, linePaint);
            }
            canvas.drawCircle(x, y, dp(2.5f), pointPaint);
            lastX = x;
            lastY = y;
        }

        canvas.drawText(CurrencyFormatter.euros(max), leftPad, topPad + dp(2f), labelPaint);
        canvas.drawText(CurrencyFormatter.euros(min), leftPad, bottomY - dp(4f), labelPaint);

        BudgetChartPoint first = points.get(0);
        BudgetChartPoint last = points.get(points.size() - 1);
        canvas.drawText(first.getLabel(), leftPad, getHeight() - dp(8f), labelPaint);
        float endLabelWidth = labelPaint.measureText(last.getLabel());
        canvas.drawText(last.getLabel(), leftPad + width - endLabelWidth, getHeight() - dp(8f), labelPaint);
    }

    private float dp(float value) {
        return value * density;
    }
}
