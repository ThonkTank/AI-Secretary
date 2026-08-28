package de.thonktank.autosecretary.domain.model;

/** One atomically logged work set. */
public final class TrainingSetResult {
    public enum Source { USER, SYNTHETIC, LEGACY }
    public enum SafetyFlag { NONE, PAIN_OR_TECHNIQUE }

    public final int repetitions;
    public final ResistanceLoad load;
    /** 0-5, where 5 represents five or more repetitions in reserve; null for legacy data. */
    public final Integer rir;
    public final Source source;
    public final SafetyFlag safetyFlag;

    public TrainingSetResult(int repetitions, ResistanceLoad load, Integer rir,
                             Source source, SafetyFlag safetyFlag) {
        RepetitionProgress.requireRecordableValue(repetitions);
        if (load == null || source == null || safetyFlag == null || rir != null && (rir < 0 || rir > 5))
            throw new IllegalArgumentException("Invalid training set result");
        this.repetitions = repetitions;
        this.load = load;
        this.rir = rir;
        this.source = source;
        this.safetyFlag = safetyFlag;
    }

    public static TrainingSetResult user(int repetitions, ResistanceLoad load, int rir) {
        return new TrainingSetResult(repetitions, load, rir, Source.USER, SafetyFlag.NONE);
    }
}
