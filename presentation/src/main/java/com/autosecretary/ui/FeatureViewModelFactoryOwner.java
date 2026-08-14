package com.autosecretary.ui;

import androidx.lifecycle.ViewModelProvider;

/** The only presentation-to-composition-root bridge. It exposes no concrete adapter. */
public interface FeatureViewModelFactoryOwner {
    ViewModelProvider.Factory featureViewModelFactory();
}
