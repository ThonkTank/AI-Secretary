package de.thonktank.autosecretary;

/** Port for requesting a widget refresh after widget-relevant state changes. */
@FunctionalInterface
interface WidgetInvalidator {
    void invalidate();
}
