package de.thonktank.autosecretary.ui.today;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;

/** Empty debug host used by the on-device Today gesture contract tests. */
public final class TodayInteractionHarnessActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new FrameLayout(this));
    }
}
