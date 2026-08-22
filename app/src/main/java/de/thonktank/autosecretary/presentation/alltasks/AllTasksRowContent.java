package de.thonktank.autosecretary.presentation.alltasks;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.time.LocalDate;
import java.util.Objects;

/** Typed immutable values used by RecyclerView content comparison. */
abstract class AllTasksRowContent {
    @Override public abstract boolean equals(Object other);
    @Override public abstract int hashCode();

    static final class TaskHeader extends AllTasksRowContent {
        final String title;
        final boolean archived;
        final boolean expanded;
        final int stepCount;
        final LocalDate nextDueOn;
        final Recurrence recurrence;
        final TaskSlot slot;
        final String needle;

        TaskHeader(String title, boolean archived, boolean expanded, int stepCount,
                   LocalDate nextDueOn, Recurrence recurrence, TaskSlot slot, String needle) {
            this.title = title;
            this.archived = archived;
            this.expanded = expanded;
            this.stepCount = stepCount;
            this.nextDueOn = nextDueOn;
            this.recurrence = recurrence;
            this.slot = slot;
            this.needle = needle;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TaskHeader)) return false;
            TaskHeader value = (TaskHeader) other;
            return archived == value.archived && expanded == value.expanded
                    && stepCount == value.stepCount && Objects.equals(title, value.title)
                    && Objects.equals(nextDueOn, value.nextDueOn)
                    && recurrence == value.recurrence && slot == value.slot
                    && Objects.equals(needle, value.needle);
        }

        @Override public int hashCode() {
            return Objects.hash(title, archived, expanded, stepCount, nextDueOn,
                    recurrence, slot, needle);
        }
    }

    static final class Step extends AllTasksRowContent {
        final String text;
        final String note;
        final boolean archived;
        final String needle;

        Step(String text, String note, boolean archived, String needle) {
            this.text = text;
            this.note = note;
            this.archived = archived;
            this.needle = needle;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Step)) return false;
            Step value = (Step) other;
            return archived == value.archived && Objects.equals(text, value.text)
                    && Objects.equals(note, value.note) && Objects.equals(needle, value.needle);
        }

        @Override public int hashCode() { return Objects.hash(text, note, archived, needle); }
    }

    static final class AddStep extends AllTasksRowContent {
        final String cardKey;
        final boolean archived;

        AddStep(String cardKey, boolean archived) {
            this.cardKey = cardKey;
            this.archived = archived;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof AddStep)) return false;
            AddStep value = (AddStep) other;
            return archived == value.archived && Objects.equals(cardKey, value.cardKey);
        }

        @Override public int hashCode() { return Objects.hash(cardKey, archived); }
    }

    static final class Target extends AllTasksRowContent {
        final String owner;
        final String beforeId;

        Target(String owner, String beforeId) {
            this.owner = owner;
            this.beforeId = beforeId;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Target)) return false;
            Target value = (Target) other;
            return Objects.equals(owner, value.owner)
                    && Objects.equals(beforeId, value.beforeId);
        }

        @Override public int hashCode() { return Objects.hash(owner, beforeId); }
    }

    static final class SlotHeader extends AllTasksRowContent {
        final TaskSlot slot;

        SlotHeader(TaskSlot slot) { this.slot = slot; }

        @Override public boolean equals(Object other) {
            return this == other || other instanceof SlotHeader
                    && slot == ((SlotHeader) other).slot;
        }

        @Override public int hashCode() { return Objects.hash(slot); }
    }

    static final class Schedule extends AllTasksRowContent {
        final String title;
        final TaskSlot slot;
        final long displayOrder;
        final Recurrence recurrence;

        Schedule(String title, TaskSlot slot, long displayOrder, Recurrence recurrence) {
            this.title = title;
            this.slot = slot;
            this.displayOrder = displayOrder;
            this.recurrence = recurrence;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Schedule)) return false;
            Schedule value = (Schedule) other;
            return displayOrder == value.displayOrder && Objects.equals(title, value.title)
                    && slot == value.slot && recurrence == value.recurrence;
        }

        @Override public int hashCode() {
            return Objects.hash(title, slot, displayOrder, recurrence);
        }
    }

    static final class Empty extends AllTasksRowContent {
        final AllTasksRow.EmptyReason reason;

        Empty(AllTasksRow.EmptyReason reason) { this.reason = reason; }

        @Override public boolean equals(Object other) {
            return this == other || other instanceof Empty && reason == ((Empty) other).reason;
        }

        @Override public int hashCode() { return Objects.hash(reason); }
    }
}
