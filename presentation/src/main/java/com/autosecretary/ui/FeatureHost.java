package com.autosecretary.ui;

import com.autosecretary.application.LocationPort;
import com.autosecretary.application.TimeProvider;
import com.autosecretary.ui.update.UpdateUiEffect;

/** Small host contract implemented by the app composition layer. */
public interface FeatureHost {
    TimeProvider timeProvider();
    LocationPort locationPort();
    void refreshWidgets();
    boolean updatesEnabled();
    boolean canInstallPackages();
    void handleUpdateEffect(UpdateUiEffect effect);
    int databaseVersion();
}
