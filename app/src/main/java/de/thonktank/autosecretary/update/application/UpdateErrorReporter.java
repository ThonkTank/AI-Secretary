package de.thonktank.autosecretary.update.application;

import de.thonktank.autosecretary.update.domain.UpdateFailure;

/** Observability port kept outside the presentation layer. */
@FunctionalInterface
public interface UpdateErrorReporter {
    void report(UpdateFailure failure);
}
