package de.thonktank.autosecretary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small action fixture that keeps legacy view tests coupled to the Today contract. */
final class TodayActionRecorder implements
        de.thonktank.autosecretary.presentation.today.TodayActionSink {
    private final List<de.thonktank.autosecretary.presentation.today.TodayAction> todayActions =
            new ArrayList<>();

    @Override public void emit(
            de.thonktank.autosecretary.presentation.today.TodayAction action) {
        todayActions.add(action);
    }

    List<de.thonktank.autosecretary.presentation.today.TodayAction> todayActions() {
        return Collections.unmodifiableList(todayActions);
    }

    de.thonktank.autosecretary.presentation.today.TodayAction lastToday(
            de.thonktank.autosecretary.presentation.today.TodayAction.Kind kind) {
        List<de.thonktank.autosecretary.presentation.today.TodayAction> values = todayActions();
        for (int index = values.size() - 1; index >= 0; index--)
            if (values.get(index).kind == kind) return values.get(index);
        return null;
    }
}
