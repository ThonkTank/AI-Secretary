package com.aisecretary.taskmaster.utils;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.aisecretary.taskmaster.adapter.TaskAdapter;
import com.aisecretary.taskmaster.database.TaskEntity;

/**
 * SwipeHelper - Handles swipe gestures on RecyclerView items
 *
 * Right swipe: Complete/Uncomplete task
 * Left swipe: Delete task
 */
public class SwipeHelper extends ItemTouchHelper.SimpleCallback {

    private TaskAdapter adapter;
    private SwipeListener listener;
    private Paint paint;

    /**
     * Interface for swipe action callbacks
     */
    public interface SwipeListener {
        void onSwipeRight(TaskEntity task, int position);
        void onSwipeLeft(TaskEntity task, int position);
    }

    /**
     * Constructor
     *
     * @param adapter TaskAdapter instance
     * @param listener SwipeListener for callbacks
     */
    public SwipeHelper(TaskAdapter adapter, SwipeListener listener) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.adapter = adapter;
        this.listener = listener;
        this.paint = new Paint();
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                         @NonNull RecyclerView.ViewHolder viewHolder,
                         @NonNull RecyclerView.ViewHolder target) {
        // We don't support drag & drop, only swipe
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getAdapterPosition();
        TaskEntity task = adapter.getTaskAt(position);

        if (direction == ItemTouchHelper.RIGHT) {
            // Right swipe: Complete/Uncomplete
            listener.onSwipeRight(task, position);
        } else if (direction == ItemTouchHelper.LEFT) {
            // Left swipe: Delete
            listener.onSwipeLeft(task, position);
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c,
                           @NonNull RecyclerView recyclerView,
                           @NonNull RecyclerView.ViewHolder viewHolder,
                           float dX, float dY,
                           int actionState,
                           boolean isCurrentlyActive) {

        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            View itemView = viewHolder.itemView;
            float height = (float) itemView.getBottom() - (float) itemView.getTop();
            float width = height / 3;

            if (dX > 0) {
                // Right swipe: Green background (Complete)
                paint.setColor(Color.parseColor("#4CAF50")); // Green
                RectF background = new RectF(
                    (float) itemView.getLeft(),
                    (float) itemView.getTop(),
                    dX,
                    (float) itemView.getBottom()
                );
                c.drawRect(background, paint);

                // Draw checkmark icon (text)
                paint.setColor(Color.WHITE);
                paint.setTextSize(48);
                paint.setTextAlign(Paint.Align.LEFT);

                String icon = "✓";
                float textX = itemView.getLeft() + width;
                float textY = itemView.getTop() + height / 2 + 16;
                c.drawText(icon, textX, textY, paint);

            } else if (dX < 0) {
                // Left swipe: Red background (Delete)
                paint.setColor(Color.parseColor("#F44336")); // Red
                RectF background = new RectF(
                    (float) itemView.getRight() + dX,
                    (float) itemView.getTop(),
                    (float) itemView.getRight(),
                    (float) itemView.getBottom()
                );
                c.drawRect(background, paint);

                // Draw delete icon (text)
                paint.setColor(Color.WHITE);
                paint.setTextSize(48);
                paint.setTextAlign(Paint.Align.RIGHT);

                String icon = "🗑";
                float textX = itemView.getRight() - width;
                float textY = itemView.getTop() + height / 2 + 16;
                c.drawText(icon, textX, textY, paint);
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

    /**
     * Set swipe threshold (fraction of view width to trigger swipe)
     */
    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return 0.4f; // Require 40% swipe to trigger action
    }

    /**
     * Set swipe escape velocity (speed to trigger instant swipe)
     */
    @Override
    public float getSwipeEscapeVelocity(float defaultValue) {
        return defaultValue * 1.5f;
    }

    /**
     * Set swipe velocity threshold (minimum speed to count as swipe)
     */
    @Override
    public float getSwipeVelocityThreshold(float defaultValue) {
        return defaultValue * 0.5f;
    }
}
