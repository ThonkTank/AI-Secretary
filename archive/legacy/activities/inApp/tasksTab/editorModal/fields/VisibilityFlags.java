package activities.inApp.tasksTab.editorModal.fields;

import entities.TrackedItem.ItemType;
import entities.TrackedItem.RepetitionType;
import entities.TrackedItem.RepUnits;

/**
 * Immutable Sichtbarkeits-Flags fuer alle Field-Gruppen.
 * Reine Funktion: berechnet aus aktuellen UI-Werten, keine Seiteneffekte.
 * Ersetzt FieldState.VisibilityModel.
 */
public record VisibilityFlags(
    // Kontext-Inputs (durchgereicht fuer Gruppen die sie brauchen)
    ItemType type,
    int schedulingType,
    RepetitionType repType,
    RepUnits repUnit,
    String repValue,
    boolean progressEnabled,
    boolean budgetEnabled,
    int predecessorIdx,

    // Berechnete Flags
    boolean showSchedulingType,
    boolean showFixedFields,
    boolean showDeadline,
    boolean showGoalAppearance,
    boolean showRepetition,
    boolean showWeekday,
    boolean showCompleteFirst,
    boolean showBudgetToggle,
    boolean showBudgetFields,
    boolean showPriority,
    boolean showCooldown,
    boolean showCompletionToggle,
    boolean showProgressFields,
    boolean showMinDuration,
    boolean showMaxDuration,
    boolean showParent,
    boolean showPredecessor,
    boolean showPredecessorDelay
) {
    public static final int SCHED_TERMIN = 0;
    public static final int SCHED_AUFGABE = 1;
    public static final int SCHED_ANGEWOHNHEIT = 2;

    /** Berechnet alle Flags aus den aktuellen UI-Werten. */
    public static VisibilityFlags compute(ItemType type, int schedulingType,
            RepetitionType repType, RepUnits repUnit, String repValue,
            boolean progressEnabled, boolean budgetEnabled, int predecessorIdx) {

        boolean isProject = type == ItemType.PROJECT;
        boolean isTask = type == ItemType.TASK;
        boolean isTermin = schedulingType == SCHED_TERMIN;
        boolean isAufgabe = schedulingType == SCHED_AUFGABE;
        boolean isAngewohnheit = schedulingType == SCHED_ANGEWOHNHEIT;

        return new VisibilityFlags(
            type, schedulingType, repType, repUnit, repValue,
            progressEnabled, budgetEnabled, predecessorIdx,
            /* showSchedulingType */    !isProject,
            /* showFixedFields */       isTermin,
            /* showDeadline */          !isProject && isAufgabe,
            /* showGoalAppearance */    type == ItemType.GOAL,
            /* showRepetition */        !isProject && isAngewohnheit,
            /* showWeekday */           repType == RepetitionType.DAY_OF_TIME && repUnit == RepUnits.WEEK,
            /* showCompleteFirst */     !isProject && isAngewohnheit,
            /* showBudgetToggle */      isTask,
            /* showBudgetFields */      isTask && budgetEnabled,
            /* showPriority */          isProject || !isTermin,
            /* showCooldown */          !isProject && (isAufgabe || isAngewohnheit),
            /* showCompletionToggle */  !isProject,
            /* showProgressFields */    !isProject && progressEnabled,
            /* showMinDuration */       !isProject,
            /* showMaxDuration */       !isProject,
            /* showParent */            !isProject,
            /* showPredecessor */       isTask,
            /* showPredecessorDelay */  isTask && predecessorIdx > 0
        );
    }
}
