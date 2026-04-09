package com.autosecretary.features.task.data;

import java.util.List;

/**
 * Placeholder data capability: persistence boundary for generated plans.
 */
public interface PlanWriteGateway {

    /**
     * Saves a generated daily plan draft.
     *
     * @param orderedTaskIds placeholder ordered plan entries
     */
    void saveDraftPlan(List<String> orderedTaskIds);
}
