package com.autosecretary.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Saves one relative instruction instead of freezing every open item. */
public final class MoveWorkItemUseCase {
    public enum Direction { FIRST, EARLIER, LATER, LAST }

    private final WorkItemRepository repository;
    private final AppClock clock;

    public MoveWorkItemUseCase(WorkItemRepository repository, AppClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void execute(String id, Direction direction, LocalDate day, List<String> visibleOrder) {
        int current = visibleOrder.indexOf(id);
        if (current < 0) return;
        DayPlanDirective.Relation relation;
        String anchor = null;
        switch (direction) {
            case FIRST -> relation = DayPlanDirective.Relation.FIRST;
            case LAST -> relation = DayPlanDirective.Relation.LAST;
            case EARLIER -> {
                if (current == 0) return;
                relation = DayPlanDirective.Relation.BEFORE;
                anchor = visibleOrder.get(current - 1);
            }
            case LATER -> {
                if (current >= visibleOrder.size() - 1) return;
                relation = DayPlanDirective.Relation.AFTER;
                anchor = visibleOrder.get(current + 1);
            }
            default -> throw new IllegalStateException("Unbekannte Verschiebung");
        }
        repository.saveDirective(new DayPlanDirective(UUID.randomUUID().toString(), day, id,
                relation, anchor, clock.now()), "Sortierung rückgängig machen");
    }

    public void executeToday(String id, Direction direction, List<String> visibleOrder) {
        execute(id, direction, clock.now().toLocalDate(), visibleOrder);
    }

    /** Removes one occurrence from today's plan without changing or completing its work item. */
    public void omitToday(String id) {
        repository.saveDirective(new DayPlanDirective(UUID.randomUUID().toString(),
                clock.now().toLocalDate(), id, DayPlanDirective.Relation.OMIT, null, clock.now()),
                "Aus heute genommen · zurückholen");
    }
}
