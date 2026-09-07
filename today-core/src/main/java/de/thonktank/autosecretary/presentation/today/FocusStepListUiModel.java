package de.thonktank.autosecretary.presentation.today;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Complete projected step list consumed by the Today focus-card renderer. */
public final class FocusStepListUiModel {
    public final String itemId;
    public final List<FocusStepRowUiModel> rows;
    public final int doneCount;

    FocusStepListUiModel(String itemId, List<FocusStepRowUiModel> rows, int doneCount) {
        if (itemId == null || rows == null)
            throw new IllegalArgumentException("Focus-step list identity is required");
        this.itemId = itemId;
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        this.doneCount = Math.max(0, doneCount);
    }

    public static FocusStepListUiModel empty() {
        return new FocusStepListUiModel("", Collections.emptyList(), 0);
    }

    public FocusStepRowUiModel expandedRow() {
        for (FocusStepRowUiModel row : rows)
            if (row.mode == FocusStepRowMode.EXPANDED) return row;
        return null;
    }
}
