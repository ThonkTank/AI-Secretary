package com.autosecretary.features.task.domain.assistant;

/** The kind of mutation a proposed change performs on a task or category. */
public enum ChangeOp {
    CREATE,
    UPDATE,
    DELETE
}
