package de.thonktank.autosecretary;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Small dependency-free wrapping row for editor chips. */
public final class EditorFlowLayout extends ViewGroup {
    private final int gap;

    public EditorFlowLayout(Context context) { this(context, null); }
    public EditorFlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        gap = Math.round(8 * getResources().getDisplayMetrics().density);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int available = Math.max(0, MeasureSpec.getSize(widthMeasureSpec)
                - getPaddingLeft() - getPaddingRight());
        int x = 0, y = 0, rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            if (x > 0 && x + child.getMeasuredWidth() > available) {
                x = 0; y += rowHeight + gap; rowHeight = 0;
            }
            x += child.getMeasuredWidth() + gap;
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
        }
        y += rowHeight;
        setMeasuredDimension(resolveSize(MeasureSpec.getSize(widthMeasureSpec), widthMeasureSpec),
                resolveSize(y + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int available = right - left - getPaddingLeft() - getPaddingRight();
        int x = 0, y = getPaddingTop(), rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            if (x > 0 && x + child.getMeasuredWidth() > available) {
                x = 0; y += rowHeight + gap; rowHeight = 0;
            }
            int childLeft = getPaddingLeft() + x;
            child.layout(childLeft, y, childLeft + child.getMeasuredWidth(),
                    y + child.getMeasuredHeight());
            x += child.getMeasuredWidth() + gap;
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
        }
    }
}
