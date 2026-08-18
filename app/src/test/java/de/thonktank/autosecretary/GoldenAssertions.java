package de.thonktank.autosecretary;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Golden comparison with CI-safe baseline updates and failure triplets. */
final class GoldenAssertions {
    private GoldenAssertions() { }

    static void compare(Class<?> owner, String resource, File baseline, File reportStem,
                        Bitmap actual, int channelTolerance, double maximumChangedRatio,
                        String updateEnvironment) throws Exception {
        write(artifact(reportStem, "actual"), actual);
        boolean update = "1".equals(System.getenv(updateEnvironment));
        if (update) {
            if (!baselineUpdatesAllowed(true, isCi()))
                throw new AssertionError("CI may not update golden baselines");
            write(baseline, actual);
            return;
        }
        try (InputStream stream = owner.getResourceAsStream(resource)) {
            assertNotNull("Missing golden " + resource, stream);
            Bitmap expected = BitmapFactory.decodeStream(stream);
            assertNotNull("Unreadable golden " + resource, expected);
            int expectedWidth = expected.getWidth();
            int expectedHeight = expected.getHeight();
            boolean sameSize = expectedWidth == actual.getWidth()
                    && expectedHeight == actual.getHeight();
            int width = Math.min(expectedWidth, actual.getWidth());
            int height = Math.min(expectedHeight, actual.getHeight());
            int[] expectedPixels = new int[width * height];
            int[] actualPixels = new int[width * height];
            expected.getPixels(expectedPixels, 0, width, 0, 0, width, height);
            actual.getPixels(actualPixels, 0, width, 0, 0, width, height);
            int[] differencePixels = actualPixels.clone();
            int changed = 0;
            for (int index = 0; index < actualPixels.length; index++) {
                if (!changed(expectedPixels[index], actualPixels[index], channelTolerance))
                    continue;
                differencePixels[index] = 0xffff00ff;
                changed++;
            }
            double ratio = sameSize ? changed / (double) Math.max(1, width * height) : 1d;
            if (!sameSize || ratio > maximumChangedRatio) {
                write(artifact(reportStem, "expected"), expected);
                Bitmap difference = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                difference.setPixels(differencePixels, 0, width, 0, 0, width, height);
                write(artifact(reportStem, "diff"), difference);
                difference.recycle();
                expected.recycle();
                throw new AssertionError(reportStem.getName() + " golden difference was " + ratio
                        + " (expected " + expectedWidth + "x" + expectedHeight
                        + ", actual " + actual.getWidth() + "x" + actual.getHeight() + ")");
            }
            expected.recycle();
        }
    }

    static boolean baselineUpdatesAllowed(boolean requested, boolean ci) {
        return requested && !ci;
    }

    private static boolean isCi() {
        return present("CI") || present("GITHUB_ACTIONS");
    }

    private static boolean present(String variable) {
        String value = System.getenv(variable);
        return value != null && !value.isEmpty() && !"false".equalsIgnoreCase(value);
    }

    private static boolean changed(int expected, int actual, int tolerance) {
        if (tolerance == 0) return expected != actual;
        return Math.max(Math.abs(((expected >>> 16) & 255) - ((actual >>> 16) & 255)),
                Math.max(Math.abs(((expected >>> 8) & 255) - ((actual >>> 8) & 255)),
                        Math.abs((expected & 255) - (actual & 255)))) > tolerance;
    }

    private static File artifact(File stem, String kind) {
        return new File(stem.getParentFile(), stem.getName() + "-" + kind + ".png");
    }

    private static void write(File output, Bitmap bitmap) throws Exception {
        assertTrue(output.getParentFile().isDirectory() || output.getParentFile().mkdirs());
        try (FileOutputStream stream = new FileOutputStream(output)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
    }
}
