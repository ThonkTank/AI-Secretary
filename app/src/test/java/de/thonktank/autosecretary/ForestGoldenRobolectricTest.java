package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.stream.Collectors;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class ForestGoldenRobolectricTest {
    @Test public void forestMatchesGoldenAtAllEightTimeAnchors() throws Exception {
        String expected;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/golden/forest-anchors.sha256"),
                StandardCharsets.UTF_8))) {
            expected = reader.lines().collect(Collectors.joining("\n"));
        }
        Context context = ApplicationProvider.getApplicationContext();
        DayPaletteInterpolator palettes = new DayPaletteInterpolator();
        StringBuilder actual = new StringBuilder();
        for (DayPaletteAnchor anchor : DayPaletteAnchor.values()) {
            ForestBackdropView forest = new ForestBackdropView(context);
            forest.setPalette(palettes.atAnchor(anchor));
            int width = 180, height = 320;
            forest.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            forest.layout(0, 0, width, height);
            forest.onSizeChanged(width, height, 0, 0);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            forest.onDraw(new Canvas(bitmap));
            if (actual.length() > 0) actual.append('\n');
            actual.append(anchor.name()).append('|').append(hash(bitmap));
            bitmap.recycle();
        }
        assertEquals("Actual forest goldens:\n" + actual, expected, actual.toString());
    }

    private static String hash(Bitmap bitmap) throws Exception {
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0,
                bitmap.getWidth(), bitmap.getHeight());
        ByteBuffer bytes = ByteBuffer.allocate(pixels.length * Integer.BYTES);
        for (int pixel : pixels) bytes.putInt(pixel);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.array());
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) hex.append(String.format("%02x", value));
        return hex.toString();
    }
}
