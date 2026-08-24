package de.thonktank.autosecretary.ui.today;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;

import de.thonktank.autosecretary.PresentationTrace;

/** Empty debug host used by the on-device Today gesture contract tests. */
public final class TodayInteractionHarnessActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PresentationTrace.emit("today-host", "create", "saved=" + (savedInstanceState != null));
        setContentView(new FrameLayout(this));
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        PresentationTrace.emit("today-host", "window-focus", "value=" + hasFocus);
    }
}
