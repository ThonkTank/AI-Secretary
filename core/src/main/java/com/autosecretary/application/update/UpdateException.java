package com.autosecretary.application.update;

/** Typed failure crossing the infrastructure boundary without Android details. */
public final class UpdateException extends RuntimeException {
    private final UpdateFailure failure;

    public UpdateException(UpdateFailure failure) {
        super(failure == null ? null : failure.detail());
        this.failure = failure == null
                ? new UpdateFailure(UpdateFailure.Kind.INTERNAL, null, true) : failure;
    }

    public UpdateException(UpdateFailure failure, Throwable cause) {
        super(failure == null ? null : failure.detail(), cause);
        this.failure = failure == null
                ? new UpdateFailure(UpdateFailure.Kind.INTERNAL, null, true) : failure;
    }

    public UpdateFailure failure() { return failure; }
}
