package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;

import android.appwidget.AppWidgetManager;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class WidgetSizeClassifierTest {
    private final WidgetSizeClassifier classifier = new WidgetSizeClassifier();

    @Test public void classifiesSmallWideTallAndLargeBoundaries() {
        assertEquals(WidgetSizeClassifier.Size.SMALL, classifier.classify(options(219, 219)));
        assertEquals(WidgetSizeClassifier.Size.WIDE, classifier.classify(options(220, 219)));
        assertEquals(WidgetSizeClassifier.Size.TALL, classifier.classify(options(309, 220)));
        assertEquals(WidgetSizeClassifier.Size.LARGE, classifier.classify(options(310, 220)));
    }

    @Test public void missingOptionsUseStableSmallDefault() {
        assertEquals(WidgetSizeClassifier.Size.SMALL, classifier.classify(null));
        assertEquals(WidgetSizeClassifier.Size.SMALL, classifier.classify(new Bundle()));
    }

    static Bundle options(int width, int height) {
        Bundle result = new Bundle();
        result.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width);
        result.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height);
        return result;
    }
}
