package de.thonktank.autosecretary.presentation.today;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Pure owner of visible order, row mode and final row action for the focus sheet. */
public final class TodayFocusProjector {
    public FocusStepListUiModel project(TodayUiModel today, String selectedStepId,
                                        TodayFeatureState.Reorder reorder) {
        if (today == null || reorder == null)
            throw new IllegalArgumentException("Today state and reorder state are required");
        FocusTaskUiModel focus = today.focus;
        if (focus == null) return FocusStepListUiModel.empty();

        List<FocusStepUiModel> open = new ArrayList<>();
        List<FocusStepUiModel> assistants = new ArrayList<>();
        int doneCount = 0;
        for (FocusStepUiModel step : focus.steps) {
            if (!step.isDone()) open.add(step);
            else {
                doneCount++;
                if (step.trainingPrompt != null) assistants.add(step);
            }
        }

        List<FocusStepUiModel> ordered = reorder.phase == TodayFeatureState.Reorder.Phase.IDLE
                ? selectedFirst(open, selectedStepId)
                : orderedBy(open, reorder.previewOrder);
        String expandedId = ordered.isEmpty() ? null : ordered.get(0).id;
        List<FocusStepRowUiModel> rows = new ArrayList<>();
        for (FocusStepUiModel step : ordered) {
            boolean expanded = step.id.equals(expandedId);
            rows.add(expanded ? FocusStepRowUiModel.expanded(step)
                    : FocusStepRowUiModel.compact(step));
        }
        for (FocusStepUiModel step : assistants)
            rows.add(FocusStepRowUiModel.assistant(step));
        return new FocusStepListUiModel(focus.occurrenceId(), rows, doneCount);
    }

    private static List<FocusStepUiModel> selectedFirst(List<FocusStepUiModel> open,
                                                         String selectedStepId) {
        List<FocusStepUiModel> result = new ArrayList<>(open);
        if (selectedStepId == null) return result;
        for (int index = 0; index < result.size(); index++) {
            if (!selectedStepId.equals(result.get(index).id)) continue;
            FocusStepUiModel selected = result.remove(index);
            result.add(0, selected);
            break;
        }
        return result;
    }

    private static List<FocusStepUiModel> orderedBy(List<FocusStepUiModel> open,
                                                     List<String> ids) {
        if (ids.size() != open.size()) return new ArrayList<>(open);
        Map<String, FocusStepUiModel> byId = new HashMap<>();
        for (FocusStepUiModel step : open) byId.put(step.id, step);
        if (byId.size() != open.size() || !byId.keySet().equals(new HashSet<>(ids)))
            return new ArrayList<>(open);
        List<FocusStepUiModel> result = new ArrayList<>();
        for (String id : ids) result.add(byId.get(id));
        return result;
    }
}
