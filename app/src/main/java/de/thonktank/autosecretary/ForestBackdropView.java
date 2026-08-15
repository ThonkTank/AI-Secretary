package de.thonktank.autosecretary;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Three quiet forest depths and the moving clock-driven light. */
final class ForestBackdropView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private DayPalette palette = DayPalette.at(java.time.LocalTime.now(), DayPalette.Mode.AUTO);

    ForestBackdropView(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    void setPalette(DayPalette palette) { this.palette = palette; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight(); if (w == 0 || h == 0) return; canvas.drawColor(palette.background);
        paint.setShader(new RadialGradient(w * palette.sunX / 100f, -h * .1f, w * palette.sunWidth,
                palette.sunColor, 0x00000000, Shader.TileMode.CLAMP)); canvas.drawRect(0, 0, w, h * .62f, paint);
        paint.setShader(null); drawLayer(canvas, w, h, palette.farAlpha, 0); drawLayer(canvas, w, h, palette.middleAlpha, 1);
        drawLayer(canvas, w, h, palette.frontAlpha, 2);
        paint.setShader(new LinearGradient(0, 0, 0, h * .12f, palette.background & 0x00ffffff,
                palette.background, Shader.TileMode.CLAMP)); canvas.drawRect(0, 0, w, h * .12f, paint); paint.setShader(null);
    }

    private void drawLayer(Canvas canvas, int w, int h, float alpha, int depth) {
        paint.setColor(palette.tree); paint.setAlpha(Math.round(alpha * 255)); paint.setStyle(Paint.Style.FILL);
        if (depth == 2) {
            Path trunk = new Path(); trunk.moveTo(-dp(42), h); trunk.lineTo(dp(62), h); trunk.lineTo(dp(42), -dp(40));
            trunk.lineTo(dp(8), -dp(40)); trunk.close(); canvas.drawPath(trunk, paint);
            branch(canvas, dp(34), h * .39f, dp(98), h * .31f, 10); branch(canvas, dp(30), h * .60f, dp(92), h * .68f, 9);
            branch(canvas, dp(37), h * .29f, dp(80), h * .23f, 7); return;
        }
        float[] xs = depth == 0 ? new float[]{.12f,.28f,.46f,.66f,.86f} : new float[]{.19f,.78f};
        for (int i = 0; i < xs.length; i++) { float x = w * xs[i], half = dp(depth == 0 ? 3 : 7);
            Path trunk = new Path(); trunk.moveTo(x-half,h); trunk.lineTo(x+half,h); trunk.lineTo(x+half-dp(7),-dp(20));
            trunk.lineTo(x-half-dp(4),-dp(20)); trunk.close(); canvas.drawPath(trunk,paint);
            branch(canvas,x,h*(.38f+i*.045f),x+(i%2==0?dp(42):-dp(42)),h*(.31f+i*.04f),depth==0?3:5);
        }
    }

    private void branch(Canvas canvas, float x1, float y1, float x2, float y2, float width) {
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.SQUARE); paint.setStrokeWidth(dp(width));
        canvas.drawLine(x1,y1,x2,y2,paint); paint.setStyle(Paint.Style.FILL);
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
