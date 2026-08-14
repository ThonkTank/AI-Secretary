package com.autosecretary.widget;

import android.content.Intent;
import android.widget.RemoteViewsService;

public final class FocusWidgetService extends RemoteViewsService {
    static final String EXTRA_MAX_ROWS = "max_rows";
    static final String EXTRA_SHOW_STEPS = "show_steps";
    static final String EXTRA_WIDE = "wide";
    static final String EXTRA_PALETTE = "palette";
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new FocusWidgetFactory(
                getApplicationContext(),
                WidgetDependencies.from(this),
                Math.max(1, intent.getIntExtra(EXTRA_MAX_ROWS, 3)),
                intent.getBooleanExtra(EXTRA_SHOW_STEPS, true),
                intent.getBooleanExtra(EXTRA_WIDE, false),
                Math.max(0, Math.min(2, intent.getIntExtra(EXTRA_PALETTE, 0))));
    }
}
