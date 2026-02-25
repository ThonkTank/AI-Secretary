package com.autosecretary.features.budget.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.autosecretary.features.budget.ui.state.BudgetChartPoint;

import java.util.ArrayList;
import java.util.List;

public class BudgetBalanceChartView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<BudgetChartPoint> points = new ArrayList<>();

    public BudgetBalanceChartView(Context context) {
        this(context, null);
    }

    public BudgetBalanceChartView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BudgetBalanceChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        linePaint.setColor(Color.parseColor("#1976D2"));
        linePaint.setStrokeWidth(dp(2f));
        linePaint.setStyle(Paint.Style.STROKE);

        pointPaint.setColor(Color.parseColor("#1976D2"));
        pointPaint.setStyle(Paint.Style.FILL);

        axisPaint.setColor(Color.parseColor("#BDBDBD"));
        axisPaint.setStrokeWidth(dp(1f));

        labelPaint.setColor(Color.parseColor("#616161"));
        labelPaint.setTextSize(dp(10f));
    }

    public void setPoints(List<BudgetChartPoint> points) {
        this.points = points != null ? points : new ArrayList<>();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float leftPad = dp(16f);
        float rightPad = dp(12f);
        float topPad = dp(12f);
        float bottomPad = dp(28f);

        float width = getWidth() - leftPad - rightPad;
        float height = getHeight() - topPad - bottomPad;
        if (width <= 0 || height <= 0) return;

        float bottomY = topPad + height;
        canvas.drawLine(leftPad, bottomY, leftPad + width, bottomY, axisPaint);

        if (points == null || points.isEmpty()) {
            canvas.drawText("Keine Chart-Daten", leftPad, topPad + height / 2f, labelPaint);
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

        canvas.drawText(formatEuro(max), leftPad, topPad + dp(2f), labelPaint);
        canvas.drawText(formatEuro(min), leftPad, bottomY - dp(4f), labelPaint);

        BudgetChartPoint first = points.get(0);
        BudgetChartPoint last = points.get(points.size() - 1);
        canvas.drawText(first.getLabel(), leftPad, getHeight() - dp(8f), labelPaint);
        float endLabelWidth = labelPaint.measureText(last.getLabel());
        canvas.drawText(last.getLabel(), leftPad + width - endLabelWidth, getHeight() - dp(8f), labelPaint);
    }

    private String formatEuro(long cents) {
        return String.format("%.0f €", cents / 100f);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
