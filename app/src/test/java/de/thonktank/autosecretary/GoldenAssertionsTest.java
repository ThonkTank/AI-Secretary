package de.thonktank.autosecretary;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class GoldenAssertionsTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void baselineUpdatePolicyIsExplicitlyDisabledOnCi() {
        assertTrue(GoldenAssertions.baselineUpdatesAllowed(true, false));
        assertFalse(GoldenAssertions.baselineUpdatesAllowed(true, true));
        assertFalse(GoldenAssertions.baselineUpdatesAllowed(false, false));
    }

    @Test public void updateRequiresAnExactPreviouslyGeneratedReviewTriplet() throws Exception {
        File folder = temporary.newFolder("goldens");
        File stem = new File(folder, "focus");
        Bitmap expected = bitmap(0xff102030);
        Bitmap actual = bitmap(0xff405060);
        Bitmap difference = bitmap(0xffff00ff);

        assertFalse(GoldenAssertions.reviewedArtifactsAvailable(
                stem, expected, actual, difference));
        write(new File(folder, "focus-expected.png"), expected);
        write(new File(folder, "focus-actual.png"), actual);
        write(new File(folder, "focus-diff.png"), difference);
        assertTrue(GoldenAssertions.reviewedArtifactsAvailable(
                stem, expected, actual, difference));

        actual.eraseColor(0xff708090);
        assertFalse(GoldenAssertions.reviewedArtifactsAvailable(
                stem, expected, actual, difference));
        expected.recycle();
        actual.recycle();
        difference.recycle();
    }

    @Test public void newBaselineRequiresAnExactPreviouslyGeneratedActual() throws Exception {
        File folder = temporary.newFolder("new-goldens");
        File stem = new File(folder, "all-tasks");
        Bitmap actual = bitmap(0xff405060);

        assertFalse(GoldenAssertions.reviewedNewArtifactAvailable(stem, actual));
        write(new File(folder, "all-tasks-actual.png"), actual);
        assertTrue(GoldenAssertions.reviewedNewArtifactAvailable(stem, actual));
        actual.eraseColor(0xff708090);
        assertFalse(GoldenAssertions.reviewedNewArtifactAvailable(stem, actual));

        actual.recycle();
    }

    private static Bitmap bitmap(int color) {
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }

    private static void write(File file, Bitmap bitmap) throws Exception {
        try (FileOutputStream stream = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
    }
}
